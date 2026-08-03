package com.example.basin.evacuation.application;

import com.example.basin.evacuation.domain.decision.Decision;
import com.example.basin.evacuation.domain.decision.DecisionRepository;
import com.example.basin.evacuation.domain.notification.NotificationOutbox;
import com.example.basin.evacuation.domain.notification.OutboxRepository;
import com.example.basin.evacuation.domain.override.ManualOverride;
import com.example.basin.evacuation.domain.override.OverrideRepository;
import com.example.basin.evacuation.domain.shared.DecisionLevel;
import com.example.basin.evacuation.domain.snapshot.RiskSnapshot;
import com.example.basin.evacuation.support.AbstractIntegrationTest;
import com.example.basin.evacuation.support.SnapshotTestFactory;
import com.example.basin.evacuation.support.TestClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IdempotentOverrideIntegrationTest extends AbstractIntegrationTest {

    @Autowired DecisionService decisionService;
    @Autowired SnapshotService snapshotService;
    @Autowired OverrideRepository overrideRepository;
    @Autowired DecisionRepository decisionRepository;
    @Autowired OutboxRepository outboxRepository;
    @Autowired TestClock clock;

    private String ingestSnapshot(String id, String versionTag) {
        RiskSnapshot snap = SnapshotTestFactory.healthy(id)
                .rainfallVersion("rain-" + versionTag)
                .waterLevelVersion("wl-" + versionTag)
                .hazardVersion("hz-" + versionTag)
                .roadVersion("rd-" + versionTag)
                .build();
        snapshotService.ingest(snap);
        return id;
    }

    @Test
    void sameRequestNo_createsOverrideExactlyOnce() {
        String snapshotId = ingestSnapshot("snap-idem-" + System.nanoTime(), "v1");
        String requestNo = "req-" + System.nanoTime();
        Instant expiresAt = clock.instant().plus(Duration.ofMinutes(30));

        Decision first = decisionService.applyOverride(
                snapshotId, requestNo, "op", "reason", DecisionLevel.RED, expiresAt);
        Decision second = decisionService.applyOverride(
                snapshotId, requestNo, "op", "reason", DecisionLevel.RED, expiresAt);
        Decision third = decisionService.applyOverride(
                snapshotId, requestNo, "different-op", "ignored", DecisionLevel.BLUE,
                clock.instant().plus(Duration.ofHours(2)));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(third.getId()).isEqualTo(first.getId());
        assertThat(first.getLevel()).isEqualTo(DecisionLevel.RED);
        assertThat(overrideRepository.findByRequestNo(requestNo)).isPresent();
        assertThat(overrideRepository.findBySnapshotIdOrderByIdDesc(snapshotId)).hasSize(1);
        assertThat(decisionRepository.findBySnapshotIdOrderBySequenceNoDesc(snapshotId)).hasSize(1);
    }

    @Test
    void concurrentReplayOfSameRequestNo_producesOneDecisionAndOneOutboxSet() throws Exception {
        String snapshotId = ingestSnapshot("snap-concur-" + System.nanoTime(), "v1");
        String requestNo = "req-concur-" + System.nanoTime();
        Instant expiresAt = clock.instant().plus(Duration.ofMinutes(30));

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Long> decisionIds = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    Decision d = decisionService.applyOverride(
                            snapshotId, requestNo, "op", "reason", DecisionLevel.RED, expiresAt);
                    synchronized (decisionIds) {
                        decisionIds.add(d.getId());
                    }
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

        Set<Long> uniqueIds = decisionIds.stream().collect(Collectors.toSet());
        assertThat(uniqueIds).hasSize(1);

        List<Decision> history = decisionRepository.findBySnapshotIdOrderBySequenceNoDesc(snapshotId);
        assertThat(history).hasSize(1);
        Decision only = history.get(0);
        assertThat(only.getLevel()).isEqualTo(DecisionLevel.RED);
        assertThat(only.getActiveOverrideId()).isNotNull();

        List<NotificationOutbox> outbox =
                outboxRepository.findByDecisionIdOrderByIdAsc(only.getId());
        assertThat(outbox).isNotEmpty();
        assertThat(outbox).allMatch(o -> o.getDecisionId().equals(only.getId()));
        assertThat(outbox).allMatch(o -> o.getPayload().requestNo().equals(requestNo));
    }

    @Test
    void twoSnapshots_haveIndependentHistoriesAndOutbox() {
        String oldId = ingestSnapshot("snap-old-" + System.nanoTime(), "old");
        String newId = ingestSnapshot("snap-new-" + System.nanoTime(), "new");

        Decision oldDecision = decisionService.recompute(oldId);
        Decision newDecision = decisionService.recompute(newId);

        assertThat(oldId).isNotEqualTo(newId);
        assertThat(oldDecision.getEvidenceVersion()).isEqualTo(oldId);
        assertThat(newDecision.getEvidenceVersion()).isEqualTo(newId);
        assertThat(oldDecision.getSequenceNo()).isEqualTo(1);
        assertThat(newDecision.getSequenceNo()).isEqualTo(1);

        assertThat(decisionService.getHistory(oldId)).hasSize(1);
        assertThat(decisionService.getHistory(newId)).hasSize(1);

        List<NotificationOutbox> oldOutbox =
                outboxRepository.findByDecisionIdOrderByIdAsc(oldDecision.getId());
        List<NotificationOutbox> newOutbox =
                outboxRepository.findByDecisionIdOrderByIdAsc(newDecision.getId());

        assertThat(oldOutbox).allMatch(o -> oldId.equals(o.getSnapshotId()));
        assertThat(newOutbox).allMatch(o -> newId.equals(o.getSnapshotId()));
        assertThat(oldOutbox).allMatch(o -> "wl-old".equals(o.getPayload().upstreamVersions().waterLevel()));
        assertThat(newOutbox).allMatch(o -> "wl-new".equals(o.getPayload().upstreamVersions().waterLevel()));
    }

    @Test
    void notificationCarriesSnapshotIdEvidenceVersionUpstreamVersionsAndRequestNo() {
        String snapshotId = ingestSnapshot("snap-payload-" + System.nanoTime(), "20260729T0600");
        String requestNo = "override-payload-" + System.nanoTime();
        Instant expiresAt = clock.instant().plus(Duration.ofMinutes(30));

        Decision decision = decisionService.applyOverride(
                snapshotId, requestNo, "op", "reason", DecisionLevel.RED, expiresAt);

        List<NotificationOutbox> outbox =
                outboxRepository.findByDecisionIdOrderByIdAsc(decision.getId());
        assertThat(outbox).isNotEmpty();

        for (NotificationOutbox o : outbox) {
            assertThat(o.getPayload().snapshotId()).isEqualTo(snapshotId);
            assertThat(o.getPayload().evidenceVersion()).isEqualTo(snapshotId);
            assertThat(o.getPayload().requestNo()).isEqualTo(requestNo);
            assertThat(o.getPayload().upstreamVersions().rainfall()).isEqualTo("rain-20260729T0600");
            assertThat(o.getPayload().upstreamVersions().waterLevel()).isEqualTo("wl-20260729T0600");
            assertThat(o.getPayload().upstreamVersions().hazard()).isEqualTo("hz-20260729T0600");
            assertThat(o.getPayload().upstreamVersions().road()).isEqualTo("rd-20260729T0600");
        }
    }

    @Test
    void replayAfterExpiryReturnsOriginalDecision_butRecomputeProducesNewComputedDecision() {
        String snapshotId = ingestSnapshot("snap-expiry-" + System.nanoTime(), "v1");
        String requestNo = "req-expiry-" + System.nanoTime();
        Instant expiresAt = clock.instant().plus(Duration.ofMinutes(30));

        Decision overridden = decisionService.applyOverride(
                snapshotId, requestNo, "op", "reason", DecisionLevel.RED, expiresAt);
        Decision replay = decisionService.applyOverride(
                snapshotId, requestNo, "op", "reason", DecisionLevel.RED, expiresAt);
        assertThat(replay.getId()).isEqualTo(overridden.getId());

        clock.setInstant(expiresAt.plusMillis(1));
        Decision reverted = decisionService.recompute(snapshotId);

        assertThat(reverted.getId()).isNotEqualTo(overridden.getId());
        assertThat(reverted.getLevel()).isEqualTo(DecisionLevel.ORANGE);
        assertThat(reverted.getActiveOverrideId()).isNull();
        assertThat(reverted.getSequenceNo()).isEqualTo(overridden.getSequenceNo() + 1);

        Decision replayAfterExpiry = decisionService.applyOverride(
                snapshotId, requestNo, "op", "reason", DecisionLevel.RED, expiresAt);
        assertThat(replayAfterExpiry.getId()).isEqualTo(overridden.getId());
    }
}
