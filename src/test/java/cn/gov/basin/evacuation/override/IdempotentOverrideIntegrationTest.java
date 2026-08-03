package cn.gov.basin.evacuation.override;

import cn.gov.basin.evacuation.AbstractIntegrationTest;
import cn.gov.basin.evacuation.decision.AdviceSource;
import cn.gov.basin.evacuation.decision.DecisionAdvice;
import cn.gov.basin.evacuation.decision.DecisionAdviceRepository;
import cn.gov.basin.evacuation.decision.DecisionService;
import cn.gov.basin.evacuation.domain.decision.DecisionLevel;
import cn.gov.basin.evacuation.notification.NotificationOutbox;
import cn.gov.basin.evacuation.notification.OutboxRepository;
import cn.gov.basin.evacuation.snapshot.RiskSnapshotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Import(AbstractIntegrationTest.TestClockConfig.class)
class IdempotentOverrideIntegrationTest extends AbstractIntegrationTest {

    @Autowired OverrideApplicationService applicationService;
    @Autowired OverrideService overrideService;
    @Autowired OverrideRepository overrideRepository;
    @Autowired DecisionAdviceRepository adviceRepository;
    @Autowired OutboxRepository outboxRepository;
    @Autowired RiskSnapshotRepository snapshotRepository;
    @Autowired DecisionService decisionService;

    private static final String OLD_SNAPSHOT = "sc-510182-20260729T0300";
    private static final String NEW_SNAPSHOT = "sc-510182-20260729T0600";
    private static final String REQUEST_ID = "override-17";

    private CreateOverrideCommand command(String snapshotId) {
        Instant now = clockNow();
        return new CreateOverrideCommand(
                "510182",
                "duty-zhao",
                "上游堰塞湖险情升级，对旧快照维持立即转移",
                DecisionLevel.LEVEL_4,
                now,
                now.plusSeconds(1800),
                snapshotId,
                REQUEST_ID
        );
    }

    private Instant clockNow() {
        return TEST_CLOCK.instant();
    }

    @Test
    @DisplayName("0600 快照已被迁移插入：164mm/6.18m/WARNING/OPEN/UNKNOWN/286，上游版本为 T0600")
    void snapshot0600IsSeeded() {
        var snap = snapshotRepository.findById(NEW_SNAPSHOT).orElseThrow();
        assertThat(snap.getRainfall3hMm()).isEqualByComparingTo("164.00");
        assertThat(snap.getWaterLevelM()).isEqualByComparingTo("6.18");
        assertThat(snap.getVulnerablePopulation()).isEqualTo(286);
        assertThat(snap.getRainfallVersion()).isEqualTo("rain-20260729T0600");
        assertThat(snap.getWaterLevelVersion()).isEqualTo("wl-20260729T0600");
        assertThat(snap.getHazardVersion()).isEqualTo("haz-20260729T0600");
        assertThat(snap.getInfrastructureVersion()).isEqualTo("infra-20260729T0600");
    }

    @Test
    @DisplayName("requestId 幂等：重复 apply 不生成多份覆写/建议/outbox")
    void requestIdIsIdempotent() {
        OverrideApplicationResult first = applicationService.apply(command(OLD_SNAPSHOT));
        OverrideApplicationResult second = applicationService.apply(command(OLD_SNAPSHOT));
        OverrideApplicationResult third = applicationService.apply(command(OLD_SNAPSHOT));

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(third.created()).isFalse();

        assertThat(first.override().getId()).isEqualTo(second.override().getId());
        assertThat(first.advice().getId()).isEqualTo(second.advice().getId());
        assertThat(first.advice().getId()).isEqualTo(third.advice().getId());

        assertThat(overrideRepository.count()).isEqualTo(1);
        assertThat(overrideRepository.findAll().get(0).getRequestId()).isEqualTo(REQUEST_ID);

        List<DecisionAdvice> advices = adviceRepository.findAll();
        assertThat(advices).hasSize(1);
        assertThat(advices.get(0).getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(advices.get(0).getSource()).isEqualTo(AdviceSource.OVERRIDDEN);

        long outboxForRequest = outboxRepository.findAll().stream()
                .filter(o -> REQUEST_ID.equals(o.getRequestId()))
                .count();
        assertThat(outboxForRequest).isEqualTo(1);
    }

    @Test
    @DisplayName("并发重放同一 requestId：只有一份覆写、一条建议、一条 outbox")
    void concurrentReplayIsIdempotent() throws Exception {
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    applicationService.apply(command(OLD_SNAPSHOT));
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(errors.get()).isZero();
        assertThat(overrideRepository.count()).isEqualTo(1);
        assertThat(adviceRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("两个快照的证据/决策历史完全独立：0300 覆写不影响 0600 的计算")
    void historiesAreIndependent() {
        OverrideApplicationResult applied = applicationService.apply(command(OLD_SNAPSHOT));

        DecisionAdvice oldAdvice = adviceRepository
                .findFirstBySnapshotIdOrderByComputedAtDesc(OLD_SNAPSHOT).orElseThrow();
        assertThat(oldAdvice.getSource()).isEqualTo(AdviceSource.OVERRIDDEN);
        assertThat(oldAdvice.getSnapshotId()).isEqualTo(OLD_SNAPSHOT);
        assertThat(oldAdvice.getRequestId()).isEqualTo(REQUEST_ID);

        OverrideApplicationResult replay = applicationService.apply(command(OLD_SNAPSHOT));
        assertThat(replay.advice().getId()).isEqualTo(applied.advice().getId());

        assertThat(adviceRepository.findFirstBySnapshotIdOrderByComputedAtDesc(NEW_SNAPSHOT))
                .as("0600 快照不应被 0300 的覆写污染")
                .isEmpty();
    }

    @Test
    @DisplayName("通知 payload 包含 snapshotId、advice 版本、四类上游版本与 requestId")
    void outboxPayloadHasAllTrackingFields() {
        applicationService.apply(command(OLD_SNAPSHOT));

        NotificationOutbox box = outboxRepository.findAll().stream()
                .filter(o -> REQUEST_ID.equals(o.getRequestId()))
                .findFirst()
                .orElseThrow();

        Map<String, Object> payload = box.getPayload();
        assertThat(payload.get("snapshotId")).isEqualTo(OLD_SNAPSHOT);
        assertThat(payload.get("adviceId")).isNotNull();
        assertThat(payload.get("requestId")).isEqualTo(REQUEST_ID);
        assertThat(payload.get("evidenceVersions")).isInstanceOf(Map.class);
        Map<?, ?> versions = (Map<?, ?>) payload.get("evidenceVersions");
        assertThat(versions.get("rainfall")).isEqualTo("rain-v20260729.0300");
        assertThat(versions.get("waterLevel")).isEqualTo("wl-v20260729.0300");
        assertThat(versions.get("hazard")).isEqualTo("haz-v20260729.0300");
        assertThat(versions.get("infrastructure")).isEqualTo("infra-v20260729.0300");
    }

    @Test
    @DisplayName("30 分钟后覆写过期：幂等 apply 仍返回原始结果；普通重算恢复 COMPUTED，历史不删除")
    void overrideExpiresAfter30Minutes() {
        OverrideApplicationResult applied = applicationService.apply(command(OLD_SNAPSHOT));
        Instant t0 = clockNow();
        setClock(t0.plusSeconds(1800));
        overrideService.expireDueOverrides();

        OverrideApplicationResult replay = applicationService.apply(command(OLD_SNAPSHOT));
        assertThat(replay.created()).isFalse();
        assertThat(replay.advice().getId()).isEqualTo(applied.advice().getId());
        assertThat(replay.advice().getSource()).isEqualTo(AdviceSource.OVERRIDDEN);

        DecisionAdvice recomputed = decisionService.computeForSnapshot(OLD_SNAPSHOT);
        assertThat(recomputed.getSource()).isEqualTo(AdviceSource.COMPUTED);
        assertThat(recomputed.getRequestId()).isNull();

        assertThat(overrideRepository.count()).isEqualTo(1);
        assertThat(overrideRepository.findAll().get(0).getStatus().name()).isEqualTo("EXPIRED");
        assertThat(adviceRepository.findAll())
                .anyMatch(a -> a.getSource() == AdviceSource.OVERRIDDEN)
                .anyMatch(a -> a.getSource() == AdviceSource.COMPUTED);
    }

    @Test
    @DisplayName("override-17 恰好到期时，并发 8 次以 recompute-expiry-17 重算，只产生一个 COMPUTED 和一个 outbox；新快照建议与历史不变")
    void concurrentRecomputeAtExactExpiryIsIdempotent() throws Exception {
        Instant t0 = clockNow();
        CreateOverrideCommand override17 = new CreateOverrideCommand(
                "510182", "duty-zhao", "堰塞湖险情，旧快照维持转移",
                DecisionLevel.LEVEL_4,
                t0,
                t0.plusSeconds(1800),
                OLD_SNAPSHOT,
                "override-17"
        );
        OverrideApplicationResult applied = applicationService.apply(override17);
        DecisionAdvice overridden = applied.advice();
        assertThat(overridden.getSource()).isEqualTo(AdviceSource.OVERRIDDEN);

        DecisionAdvice newSnapshotBefore = decisionService.computeForSnapshot(NEW_SNAPSHOT);
        Long newAdviceIdBefore = newSnapshotBefore.getId();
        long outboxForOverride17Before = outboxRepository.findAll().stream()
                .filter(o -> "override-17".equals(o.getRequestId()))
                .count();

        Instant expiry = t0.plusSeconds(1800);
        setClock(expiry);
        overrideService.expireDueOverrides();

        long advicesBefore = adviceRepository.count();
        long outboxBefore = outboxRepository.count();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();
        List<Long> producedIds = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    DecisionAdvice a = decisionService.computeForSnapshot(
                            OLD_SNAPSHOT, "recompute-expiry-17");
                    producedIds.add(a.getId());
                    assertThat(a.getSource()).isEqualTo(AdviceSource.COMPUTED);
                    assertThat(a.getRequestId()).isEqualTo("recompute-expiry-17");
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(errors.get()).isZero();
        assertThat(producedIds).hasSize(8);
        assertThat(producedIds.stream().distinct()).hasSize(1);

        assertThat(adviceRepository.count()).isEqualTo(advicesBefore + 1);
        assertThat(outboxRepository.count()).isEqualTo(outboxBefore + 1);

        List<DecisionAdvice> recomputes = adviceRepository.findAll().stream()
                .filter(a -> "recompute-expiry-17".equals(a.getRequestId()))
                .toList();
        assertThat(recomputes).hasSize(1);
        DecisionAdvice recomputed = recomputes.get(0);
        assertThat(recomputed.getSource()).isEqualTo(AdviceSource.COMPUTED);
        assertThat(recomputed.getSnapshotId()).isEqualTo(OLD_SNAPSHOT);
        assertThat(recomputed.getOverrideId()).isNull();

        List<NotificationOutbox> recomputeOutbox = outboxRepository.findAll().stream()
                .filter(o -> "recompute-expiry-17".equals(o.getRequestId()))
                .toList();
        assertThat(recomputeOutbox).hasSize(1);
        assertThat(recomputeOutbox.get(0).getAdviceId()).isEqualTo(recomputed.getId());

        var newSnapshotAfter = adviceRepository
                .findFirstBySnapshotIdOrderByComputedAtDesc(NEW_SNAPSHOT).orElseThrow();
        assertThat(newSnapshotAfter.getId())
                .as("并发重算旧快照不得改变新快照的当前建议")
                .isEqualTo(newAdviceIdBefore);
        assertThat(newSnapshotAfter.getSnapshotId()).isEqualTo(NEW_SNAPSHOT);

        var overrideRow = overrideRepository.findByRequestId("override-17").orElseThrow();
        assertThat(overrideRow.getStatus()).isEqualTo(OverrideStatus.EXPIRED);
        assertThat(overrideRepository.count()).isEqualTo(1);

        long outboxForOverride17After = outboxRepository.findAll().stream()
                .filter(o -> "override-17".equals(o.getRequestId()))
                .count();
        assertThat(outboxForOverride17After).isEqualTo(outboxForOverride17Before);

        assertThat(adviceRepository.findAll())
                .anyMatch(a -> AdviceSource.OVERRIDDEN.equals(a.getSource())
                        && "override-17".equals(a.getRequestId()))
                .anyMatch(a -> AdviceSource.COMPUTED.equals(a.getSource())
                        && "recompute-expiry-17".equals(a.getRequestId()));
    }
}
