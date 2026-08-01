package com.basin.evacuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.basin.evacuation.common.ConflictException;
import com.basin.evacuation.decision.Decision;
import com.basin.evacuation.decision.DecisionOutcome;
import com.basin.evacuation.decision.DecisionService;
import com.basin.evacuation.decision.DecisionSource;
import com.basin.evacuation.notification.OutboxMessage;
import com.basin.evacuation.notification.OutboxRepository;
import com.basin.evacuation.override_.OverrideService;
import com.basin.evacuation.snapshot.HazardPointStatus;
import com.basin.evacuation.snapshot.RiskSnapshot;
import com.basin.evacuation.snapshot.RoadStatus;
import com.basin.evacuation.snapshot.SnapshotService;
import com.basin.evacuation.snapshot.UpstreamKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 并发重放幂等测试：创建快照、覆写、重算三类请求带相同业务请求号并发重放时，
 * 每个请求号只生成一条建议与一条通知；两个快照的证据与决策历史互不串线。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdempotentReplayTest extends PostgresIntegrationTest {

    static final String OLD_SNAPSHOT = "sc-510182-20260729T0300";
    static final String NEW_SNAPSHOT = "sc-510182-20260729T0600";

    @Autowired
    SnapshotService snapshotService;
    @Autowired
    DecisionService decisionService;
    @Autowired
    OverrideService overrideService;
    @Autowired
    OutboxRepository outboxRepository;
    @Autowired
    MutableClock clock;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    void concurrentOverrideReplay_override17_onlyOneDecisionAndNotification() throws Exception {
        // 创建时刻定在 1 小时前，30 分钟后过期（相对真实时间已过期，
        // 其它测试类按真实时间读旧快照时仍看到计算结果，互不干扰）
        Instant base = Instant.now().minusSeconds(3600);
        clock.set(base);
        Instant expiresAt = base.plusSeconds(1800);

        int threads = 6;
        List<Callable<Decision>> tasks = IntStream.range(0, threads)
                .mapToObj(i -> (Callable<Decision>) () -> overrideService.recordOverride(
                        OLD_SNAPSHOT, "override-17", DecisionOutcome.PRE_TRANSFER,
                        "值班员王五", "现场核实风险可控，暂缓一级转移", expiresAt))
                .toList();
        List<Decision> results = runConcurrently(tasks, threads);

        // 6 个并发请求全部返回同一条建议（同一个 UUID）
        assertThat(results).hasSize(threads);
        assertThat(results.stream().map(Decision::getId)).containsOnly(results.get(0).getId());

        // 恰好 1 条覆写记录、新增恰好 1 条建议、恰好 1 条通知
        assertThat(overrideService.list(OLD_SNAPSHOT)).hasSize(1);
        List<Decision> history = decisionService.history(OLD_SNAPSHOT);
        assertThat(history).hasSize(2); // 种子 seq=1 + 覆写
        Decision overrideDecision = history.stream()
                .filter(d -> d.getSource() == DecisionSource.OVERRIDE)
                .findFirst().orElseThrow();
        assertThat(overrideDecision.getRequestId()).isEqualTo("override-17");
        assertThat(outboxRepository.countByDecisionIdIn(List.of(overrideDecision.getId()))).isEqualTo(1);

        // 通知 payload 同时写明 snapshotId、snapshotVersion、四类上游版本与请求号
        OutboxMessage message = outboxRepository.findAll().stream()
                .filter(m -> m.getDecisionId().equals(overrideDecision.getId()))
                .findFirst().orElseThrow();
        assertThat(message.getPayload())
                .containsEntry("snapshotId", OLD_SNAPSHOT)
                .containsEntry("snapshotVersion", 1)
                .containsEntry("requestId", "override-17");
        assertThat(upstreamVersions(message))
                .containsOnlyKeys("RAINFALL", "WATER_LEVEL", "HAZARD_POINT", "ROAD")
                .containsEntry("RAINFALL", "rain-20260729T0300");

        // 有效期内：旧快照当前建议受覆写影响
        clock.set(base.plusSeconds(60));
        assertThat(decisionService.current(OLD_SNAPSHOT).getOutcome()).isEqualTo(DecisionOutcome.PRE_TRANSFER);
        assertThat(decisionService.current(OLD_SNAPSHOT).getSource()).isEqualTo(DecisionSource.OVERRIDE);

        // 恰好到期 / 到期后一毫秒：恢复旧快照自己的计算结果（一级·立即转移）
        clock.set(expiresAt);
        assertThat(decisionService.current(OLD_SNAPSHOT).getOutcome()).isEqualTo(DecisionOutcome.EVACUATE_NOW);
        clock.set(expiresAt.plusMillis(1));
        assertThat(decisionService.current(OLD_SNAPSHOT).getOutcome()).isEqualTo(DecisionOutcome.EVACUATE_NOW);
        assertThat(decisionService.current(OLD_SNAPSHOT).getSource()).isEqualTo(DecisionSource.COMPUTED);
    }

    @Test
    @Order(2)
    void sameRequestIdWithDifferentContentConflicts() {
        clock.set(Instant.now());
        assertThatThrownBy(() -> overrideService.recordOverride(
                OLD_SNAPSHOT, "override-17", DecisionOutcome.EVACUATE_NOW,
                "另一位值班员", "不同的理由", Instant.now().plusSeconds(600)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("override-17");
    }

    @Test
    @Order(3)
    void atExactExpiry_concurrentReadsAndIdempotentRecomputes() throws Exception {
        // 到期语义固定为 now >= expiresAt：把时钟卡在 override-17 恰好到期的一瞬
        Instant expiresAt = overrideService.list(OLD_SNAPSHOT).get(0).getExpiresAt();
        clock.set(expiresAt);

        // 基线：第 2 轮新快照当前建议、历史通知清单
        Decision newSnapshotBefore = decisionService.current(NEW_SNAPSHOT);
        List<UUID> outboxIdsBefore = outboxRepository.findAll().stream().map(OutboxMessage::getId).toList();
        long outboxCountBefore = outboxRepository.count();

        int recomputes = 8;
        int readers = 4;
        List<Callable<Object>> tasks = new java.util.ArrayList<>();
        IntStream.range(0, recomputes)
                .forEach(i -> tasks.add(() -> decisionService.recompute(OLD_SNAPSHOT, "recompute-expiry-17")));
        IntStream.range(0, readers)
                .forEach(i -> tasks.add(() -> decisionService.current(OLD_SNAPSHOT)));

        List<Object> results = runConcurrently(tasks, recomputes + readers);
        List<Decision> recomputeResults = results.subList(0, recomputes).stream()
                .map(Decision.class::cast).toList();
        List<Decision> readResults = results.subList(recomputes, results.size()).stream()
                .map(Decision.class::cast).toList();

        // 恰好到期：并发读到的永远是 COMPUTED（旧覆写已失效），即旧快照自己的一级计算结果
        assertThat(readResults).allSatisfy(d -> {
            assertThat(d.getSource()).isEqualTo(DecisionSource.COMPUTED);
            assertThat(d.getOutcome()).isEqualTo(DecisionOutcome.EVACUATE_NOW);
            assertThat(d.getSnapshotId()).isEqualTo(OLD_SNAPSHOT);
        });

        // 8 次相同请求号重算：恰好生成 1 条新 COMPUTED 建议与 1 条 outbox
        assertThat(recomputeResults.stream().map(Decision::getId))
                .containsOnly(recomputeResults.get(0).getId());
        Decision newComputed = recomputeResults.get(0);
        assertThat(newComputed.getSource()).isEqualTo(DecisionSource.COMPUTED);
        assertThat(newComputed.getRequestId()).isEqualTo("recompute-expiry-17");
        List<Decision> history = decisionService.history(OLD_SNAPSHOT);
        assertThat(history).hasSize(3); // 种子 seq1 + 覆写 seq2 + 本次重算 seq3
        assertThat(newComputed.getSeq()).isEqualTo(3);
        assertThat(outboxRepository.countByDecisionIdIn(List.of(newComputed.getId()))).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(outboxCountBefore + 1);

        // 历史通知一条不少
        List<UUID> outboxIdsAfter = outboxRepository.findAll().stream().map(OutboxMessage::getId).toList();
        assertThat(outboxIdsAfter).containsAll(outboxIdsBefore);

        // 旧覆写仍在，且不能被删除（DB 触发器强制）
        assertThat(overrideService.list(OLD_SNAPSHOT)).hasSize(1);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM manual_override WHERE request_id = 'override-17'"))
                .hasMessageContaining("immutable");

        // 第 2 轮新快照的当前建议完全不受旧快照操作影响
        Decision newSnapshotAfter = decisionService.current(NEW_SNAPSHOT);
        assertThat(newSnapshotAfter.getId()).isEqualTo(newSnapshotBefore.getId());
        assertThat(newSnapshotAfter.getOutcome()).isEqualTo(newSnapshotBefore.getOutcome());
        assertThat(newSnapshotAfter.getSeq()).isEqualTo(newSnapshotBefore.getSeq());
        assertThat(newSnapshotAfter.getSource()).isEqualTo(DecisionSource.COMPUTED);
    }

    @Test
    @Order(4)
    void newSnapshotSeedUsesOwnEvidenceAndHistoriesAreIsolated() {
        RiskSnapshot s = snapshotService.get(NEW_SNAPSHOT);
        assertThat(s.getRainfall3hMm()).isEqualByComparingTo(new BigDecimal("164.00"));
        assertThat(s.getWaterLevelM()).isEqualByComparingTo(new BigDecimal("6.180"));
        assertThat(s.getHazardPointStatus()).isEqualTo(HazardPointStatus.WARNING);
        assertThat(s.getPrimaryRoadStatus()).isEqualTo(RoadStatus.OPEN);
        assertThat(s.getSecondaryRoadStatus()).isEqualTo(RoadStatus.UNKNOWN);
        assertThat(s.getVulnerablePopulation()).isEqualTo(286);
        assertThat(s.getUpstreamHealth().get(UpstreamKind.RAINFALL).version()).isEqualTo("rain-20260729T0600");
        assertThat(s.getUpstreamHealth().get(UpstreamKind.WATER_LEVEL).version()).isEqualTo("wl-20260729T0600");
        assertThat(s.getUpstreamHealth().get(UpstreamKind.HAZARD_POINT).version()).isEqualTo("hz-20260729T0600");
        assertThat(s.getUpstreamHealth().get(UpstreamKind.ROAD).version()).isEqualTo("road-20260729T0600");

        // 新快照始终按自己的证据计算：一级·立即转移；主路 OPEN，因此没有主路封闭提示
        Decision current = decisionService.current(NEW_SNAPSHOT);
        assertThat(current.getOutcome()).isEqualTo(DecisionOutcome.EVACUATE_NOW);
        assertThat(current.getSource()).isEqualTo(DecisionSource.COMPUTED);
        assertThat(current.getReasons().get(0)).contains("164mm≥100mm").contains("6.18m≥6m");
        assertThat(current.getReasons()).doesNotContain("主路封闭：撤离路线须避开主路");

        // 两个快照的决策历史互不串线
        assertThat(decisionService.history(NEW_SNAPSHOT))
                .allSatisfy(d -> assertThat(d.getSnapshotId()).isEqualTo(NEW_SNAPSHOT));
        assertThat(decisionService.history(OLD_SNAPSHOT))
                .allSatisfy(d -> assertThat(d.getSnapshotId()).isEqualTo(OLD_SNAPSHOT));

        // 新快照的通知写明 snapshotId、snapshotVersion、四类上游版本与请求号
        Decision seedDecision = decisionService.history(NEW_SNAPSHOT).stream()
                .filter(d -> d.getSeq() == 1).findFirst().orElseThrow();
        OutboxMessage message = outboxRepository.findAll().stream()
                .filter(m -> m.getDecisionId().equals(seedDecision.getId()))
                .findFirst().orElseThrow();
        assertThat(message.getPayload())
                .containsEntry("snapshotId", NEW_SNAPSHOT)
                .containsEntry("snapshotVersion", 1)
                .containsEntry("requestId", "seed-sc-510182-20260729T0600");
        assertThat(upstreamVersions(message))
                .containsEntry("RAINFALL", "rain-20260729T0600")
                .containsEntry("WATER_LEVEL", "wl-20260729T0600")
                .containsEntry("HAZARD_POINT", "hz-20260729T0600")
                .containsEntry("ROAD", "road-20260729T0600");
    }

    @Test
    @Order(5)
    void concurrentRecomputeWithSameRequestIdIsIdempotent() throws Exception {
        int threads = 6;
        List<Callable<Decision>> tasks = IntStream.range(0, threads)
                .mapToObj(i -> (Callable<Decision>) () -> decisionService.recompute(NEW_SNAPSHOT, "recompute-1"))
                .toList();
        List<Decision> results = runConcurrently(tasks, threads);

        // 相同请求号：全部返回同一条建议，恰好新增 1 条建议与 1 条通知
        assertThat(results.stream().map(Decision::getId)).containsOnly(results.get(0).getId());
        assertThat(decisionService.history(NEW_SNAPSHOT)).hasSize(2);
        assertThat(outboxRepository.countByDecisionIdIn(List.of(results.get(0).getId()))).isEqualTo(1);

        // 换一个请求号 -> 生成新建议；不带请求号 -> 也生成新建议
        Decision d2 = decisionService.recompute(NEW_SNAPSHOT, "recompute-2");
        Decision d3 = decisionService.recompute(NEW_SNAPSHOT);
        assertThat(d2.getSeq()).isNotEqualTo(results.get(0).getSeq());
        assertThat(d3.getSeq()).isNotEqualTo(d2.getSeq());
        assertThat(decisionService.history(NEW_SNAPSHOT)).hasSize(4);
    }

    @Test
    @Order(6)
    void concurrentCreateSnapshotWithSameRequestIdIsIdempotent() throws Exception {
        String snapshotId = "t-idem-create-1";
        int threads = 6;
        List<Callable<SnapshotService.CreateSnapshotResult>> tasks = IntStream.range(0, threads)
                .mapToObj(i -> (Callable<SnapshotService.CreateSnapshotResult>) () -> snapshotService.create(
                        snapshotId, "510182", 1,
                        new BigDecimal("10.00"), new BigDecimal("4.500"),
                        HazardPointStatus.OK, RoadStatus.OPEN, RoadStatus.OPEN,
                        0, allOkUpstream(), Instant.parse("2026-08-01T03:00:00Z"), "create-1"))
                .toList();
        List<SnapshotService.CreateSnapshotResult> results = runConcurrently(tasks, threads);

        // 恰好一个请求真正创建，其余都是幂等重放
        assertThat(results).hasSize(threads);
        assertThat(results.stream().filter(r -> !r.replayed()).count()).isEqualTo(1);
        assertThat(results.stream().filter(SnapshotService.CreateSnapshotResult::replayed).count())
                .isEqualTo(threads - 1);

        // 恰好 1 条建议 + 1 条通知，请求号为 create-1
        List<Decision> history = decisionService.history(snapshotId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getRequestId()).isEqualTo("create-1");
        assertThat(outboxRepository.countByDecisionIdIn(List.of(history.get(0).getId()))).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> upstreamVersions(OutboxMessage message) {
        return (Map<String, Object>) message.getPayload().get("upstreamVersions");
    }

    private static <T> List<T> runConcurrently(List<Callable<T>> tasks, int threads) throws InterruptedException {
        try (var executor = Executors.newFixedThreadPool(threads)) {
            return executor.invokeAll(tasks).stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();
        }
    }
}
