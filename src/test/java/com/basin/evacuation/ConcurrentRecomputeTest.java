package com.basin.evacuation;

import static org.assertj.core.api.Assertions.assertThat;

import com.basin.evacuation.decision.Decision;
import com.basin.evacuation.decision.DecisionService;
import com.basin.evacuation.notification.OutboxRepository;
import com.basin.evacuation.snapshot.HazardPointStatus;
import com.basin.evacuation.snapshot.RoadStatus;
import com.basin.evacuation.snapshot.SnapshotService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 并发重算同一快照：8 个线程同时重算，必须全部成功、seq 不重不漏、
 * 每条建议各有一条 outbox —— 不允许出现写了一半的状态。
 */
class ConcurrentRecomputeTest extends PostgresIntegrationTest {

    @Autowired
    SnapshotService snapshotService;
    @Autowired
    DecisionService decisionService;
    @Autowired
    OutboxRepository outboxRepository;

    @Test
    void concurrentRecomputeIsSerializedAndAtomic() throws Exception {
        String snapshotId = "t-concurrent-1";
        snapshotService.create(snapshotId, "510182", 1,
                new BigDecimal("118.00"), new BigDecimal("6.120"),
                HazardPointStatus.WARNING, RoadStatus.CLOSED, RoadStatus.UNKNOWN,
                286, allOkUpstream(), Instant.parse("2026-08-01T01:00:00Z"));

        int threads = 8;
        List<Callable<Decision>> tasks = IntStream.range(0, threads)
                .mapToObj(i -> (Callable<Decision>) () -> decisionService.recompute(snapshotId))
                .toList();

        List<Decision> results;
        try (var executor = Executors.newFixedThreadPool(threads)) {
            results = executor.invokeAll(tasks).stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();
        }

        // 全部成功
        assertThat(results).hasSize(threads);

        // 1 条初始 + 8 条重算；seq 恰好是 1..9，不重不漏
        List<Decision> all = decisionService.history(snapshotId);
        assertThat(all).hasSize(threads + 1);
        assertThat(all.stream().map(Decision::getSeq))
                .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9);

        // 每条建议恰好一条 outbox（决策与通知同事务）
        List<UUID> decisionIds = all.stream().map(Decision::getId).toList();
        assertThat(outboxRepository.countByDecisionIdIn(decisionIds)).isEqualTo(threads + 1L);

        // 每条建议都标注了同一个证据版本
        assertThat(all).allSatisfy(d -> {
            assertThat(d.getSnapshotVersion()).isEqualTo(1);
            assertThat(d.getEvidence().snapshotId()).isEqualTo(snapshotId);
        });
    }
}
