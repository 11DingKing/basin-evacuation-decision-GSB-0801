package cn.gov.basin.evacuation.decision;

import cn.gov.basin.evacuation.AbstractIntegrationTest;
import cn.gov.basin.evacuation.domain.decision.DecisionLevel;
import cn.gov.basin.evacuation.notification.OutboxRepository;
import cn.gov.basin.evacuation.notification.OutboxStatus;
import cn.gov.basin.evacuation.override.CreateOverrideCommand;
import cn.gov.basin.evacuation.override.OverrideService;
import cn.gov.basin.evacuation.snapshot.RiskSnapshot;
import cn.gov.basin.evacuation.snapshot.RiskSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Import(AbstractIntegrationTest.TestClockConfig.class)
class DecisionFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired DecisionService decisionService;
    @Autowired OverrideService overrideService;
    @Autowired RiskSnapshotRepository snapshotRepository;
    @Autowired DecisionAdviceRepository adviceRepository;
    @Autowired OutboxRepository outboxRepository;

    private static final String INITIAL_SNAPSHOT = "sc-510182-20260729T0300";

    @BeforeEach
    void setUp() {
        setClock(Instant.parse("2026-07-29T03:05:00Z"));
    }

    @Test
    @DisplayName("初始快照触发 LEVEL_4，证据引用四类上游版本，人口单位为人，并产生一条 outbox")
    void initialSnapshotProducesLevel4AndOutbox() {
        RiskSnapshot snap = snapshotRepository.findById(INITIAL_SNAPSHOT).orElseThrow();
        assertThat(snap.getRainfall3hMm()).isEqualByComparingTo(new BigDecimal("118.00"));
        assertThat(snap.getVulnerablePopulation()).isEqualTo(286);

        DecisionAdvice advice = decisionService.computeForSnapshot(INITIAL_SNAPSHOT);

        assertThat(advice.getDecisionLevel()).isEqualTo(DecisionLevel.LEVEL_4);
        assertThat(advice.getSource()).isEqualTo(AdviceSource.COMPUTED);
        assertThat(advice.getRegionCode()).isEqualTo("510182");
        assertThat(advice.getEvidenceRef())
                .containsKey("rainfall")
                .containsKey("waterLevel")
                .containsKey("hazard")
                .containsKey("infrastructure");
        assertThat(advice.getEvidenceRef().get("source")).isEqualTo("COMPUTED");

        long pending = outboxRepository.countByStatus(OutboxStatus.PENDING);
        assertThat(pending).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("覆写生效前 -> 恰好到期 -> 到期后一瞬间，自动恢复计算结果")
    void overrideLifecycleAcrossClockBoundaries() {
        Instant t0 = Instant.parse("2026-07-29T03:05:00Z");
        Instant effective = t0.plusSeconds(60);
        Instant expires = t0.plusSeconds(300);

        overrideService.create(new CreateOverrideCommand(
                "510182", "duty-zhao", "上游堰塞湖险情，需立即转移",
                DecisionLevel.LEVEL_4, effective, expires));

        setClock(t0);
        DecisionAdvice before = decisionService.computeForSnapshot(INITIAL_SNAPSHOT);
        assertThat(before.getSource()).isEqualTo(AdviceSource.COMPUTED);
        assertThat(before.getOverrideId()).isNull();

        setClock(effective);
        DecisionAdvice atStart = decisionService.computeForSnapshot(INITIAL_SNAPSHOT);
        assertThat(atStart.getSource()).isEqualTo(AdviceSource.OVERRIDDEN);
        assertThat(atStart.getOverrideId()).isNotNull();
        assertThat(atStart.getEvidenceRef().get("overrideId")).isEqualTo(atStart.getOverrideId());

        setClock(expires);
        DecisionAdvice atExpiry = decisionService.computeForSnapshot(INITIAL_SNAPSHOT);
        assertThat(atExpiry.getSource()).isEqualTo(AdviceSource.COMPUTED);

        setClock(expires.plusMillis(1));
        DecisionAdvice after = decisionService.computeForSnapshot(INITIAL_SNAPSHOT);
        assertThat(after.getSource()).isEqualTo(AdviceSource.COMPUTED);
        assertThat(after.getDecisionLevel()).isEqualTo(DecisionLevel.LEVEL_4);

        overrideService.expireDueOverrides();
        var history = overrideService.history("510182");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getStatus().name()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("并发重算同一快照：advisory lock 保证最终建议数量等于并发数，无异常且都能成功")
    void concurrentRecomputeIsSerializableForSameSnapshot() throws Exception {
        long beforeCount = adviceRepository.count();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    decisionService.computeForSnapshot(INITIAL_SNAPSHOT);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(failures.get()).isZero();
        assertThat(success.get()).isEqualTo(threads);
        assertThat(adviceRepository.count()).isEqualTo(beforeCount + threads);
    }

    @Test
    @DisplayName("历史建议与覆写不可删除：直接 JDBC 删除应被数据库触发器拒绝")
    void immutabilityEnforcedAtDatabaseLevel() {
        DecisionAdvice advice = decisionService.computeForSnapshot(INITIAL_SNAPSHOT);

        try {
            jdbc.update("delete from decision_advices where id = ?", advice.getId());
            assertThat(false).as("deleting advice should have failed").isTrue();
        } catch (Exception expected) {
            assertThat(expected.getMessage()).containsIgnoringCase("immutable");
        }

        try {
            jdbc.update("delete from risk_snapshots where snapshot_id = ?", INITIAL_SNAPSHOT);
            assertThat(false).as("deleting snapshot should have failed").isTrue();
        } catch (Exception expected) {
            assertThat(expected.getMessage()).containsIgnoringCase("immutable");
        }

        var created = overrideService.create(new CreateOverrideCommand(
                "510182", "tester", "x",
                DecisionLevel.LEVEL_2,
                Instant.parse("2026-07-29T03:00:00Z"),
                Instant.parse("2026-07-29T04:00:00Z")));
        try {
            jdbc.update("delete from manual_overrides where id = ?", created.getId());
            assertThat(false).as("deleting override should have failed").isTrue();
        } catch (Exception expected) {
            assertThat(expected.getMessage()).containsIgnoringCase("cannot be deleted");
        }
    }

    @Test
    @DisplayName("检索：按 region/level 分页查询，latestForSnapshot 总返回最新一条")
    void searchAndLatestApis() {
        decisionService.computeForSnapshot(INITIAL_SNAPSHOT);
        tickSeconds(1);
        decisionService.computeForSnapshot(INITIAL_SNAPSHOT);

        var page = decisionService.search("510182", DecisionLevel.LEVEL_4, 0, 10);
        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent().get(0).getDecisionLevel()).isEqualTo(DecisionLevel.LEVEL_4);

        var latest = decisionService.latestForSnapshot(INITIAL_SNAPSHOT).orElseThrow();
        var newestInDb = adviceRepository.findFirstBySnapshotIdOrderByComputedAtDesc(INITIAL_SNAPSHOT).orElseThrow();
        assertThat(latest.getId()).isEqualTo(newestInDb.getId());
    }
}
