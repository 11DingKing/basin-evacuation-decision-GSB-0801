package com.example.basin.evacuation.application;

import com.example.basin.evacuation.domain.decision.Decision;
import com.example.basin.evacuation.domain.decision.DecisionRepository;
import com.example.basin.evacuation.domain.notification.OutboxRepository;
import com.example.basin.evacuation.support.AbstractIntegrationTest;
import com.example.basin.evacuation.support.SnapshotTestFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrentRecomputeIntegrationTest extends AbstractIntegrationTest {

    @Autowired DecisionService decisionService;
    @Autowired SnapshotService snapshotService;
    @Autowired DecisionRepository decisionRepository;
    @Autowired OutboxRepository outboxRepository;

    @Test
    void concurrentRecomputeOfSameSnapshot_producesContiguousUniqueSequencesAndOutbox() throws Exception {
        String snapshotId = "snap-concurrent-" + System.nanoTime();
        snapshotService.ingest(SnapshotTestFactory.healthy(snapshotId).build());

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> errors = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    decisionService.recompute(snapshotId);
                } catch (Throwable t) {
                    synchronized (errors) {
                        errors.add(t);
                    }
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(errors).isEmpty();

        List<Decision> history = decisionRepository.findBySnapshotIdOrderBySequenceNoDesc(snapshotId);
        assertThat(history).hasSize(threads);

        List<Integer> sequences = history.stream().map(Decision::getSequenceNo).toList();
        assertThat(sequences).containsExactlyInAnyOrderElementsOf(
                IntStream.rangeClosed(1, threads).boxed().toList());
        Set<Integer> unique = sequences.stream().collect(Collectors.toSet());
        assertThat(unique).hasSize(threads);

        for (Decision d : history) {
            assertThat(outboxRepository.findByDecisionIdOrderByIdAsc(d.getId()))
                    .isNotEmpty()
                    .allMatch(o -> o.getDecisionId().equals(d.getId()));
        }
    }
}
