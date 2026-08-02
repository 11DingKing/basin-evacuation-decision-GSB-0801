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
class ExpiryIdempotentRecomputeIntegrationTest extends AbstractIntegrationTest {

    private static final String RECOMPUTE_REQUEST = "recompute-expiry-17";

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

    private String ingestRedSnapshot(String id, String versionTag) {
        RiskSnapshot snap = SnapshotTestFactory.healthy(id)
                .rainfall3hMm(new java.math.BigDecimal("164.0"))
                .waterLevelM(new java.math.BigDecimal("6.18"))
                .mainRoadStatus(com.example.basin.evacuation.domain.shared.RoadStatus.OPEN)
                .rainfallVersion("rain-" + versionTag)
                .waterLevelVersion("wl-" + versionTag)
                .hazardVersion("hz-" + versionTag)
                .roadVersion("rd-" + versionTag)
                .build();
        snapshotService.ingest(snap);
        return id;
    }

    @Test
    void atExactExpiry_eightConcurrentIdempotentRecomputes_produceOneComputedDecisionAndOneOutboxBatch() throws Exception {
        String oldId = ingestSnapshot("snap-old-expiry-" + System.nanoTime(), "old");
        String newId = ingestRedSnapshot("snap-new-expiry-" + System.nanoTime(), "new");

        Instant t0 = clock.instant();
        Instant expiresAt = t0.plusSeconds(60);

        Decision redBefore = decisionService.applyOverride(
                oldId, "override-expiry-" + System.nanoTime(),
                "值班长-张三", "上游险情加剧，提前转移",
                DecisionLevel.RED, expiresAt);
        assertThat(redBefore.getLevel()).isEqualTo(DecisionLevel.RED);
        assertThat(redBefore.getActiveOverrideId()).isNotNull();

        Decision newCurrent = decisionService.recompute(newId);
        assertThat(newCurrent.getLevel()).isEqualTo(DecisionLevel.RED);
        Long newDecisionId = newCurrent.getId();

        long overrideCountBefore = overrideRepository.count();
        List<NotificationOutbox> oldRedOutboxBefore =
                outboxRepository.findByDecisionIdOrderByIdAsc(redBefore.getId());
        long totalOutboxBefore = outboxRepository.count();
        long oldRedOutboxCount = oldRedOutboxBefore.size();

        clock.setInstant(expiresAt);

        assertThat(decisionService.getById(redBefore.getId())).isPresent();
        ManualOverride override = overrideRepository.findById(redBefore.getActiveOverrideId()).orElseThrow();
        assertThat(override.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(overrideRepository.findActiveOverride(oldId, clock.instant()))
                .as("at now == expiresAt the override must be expired (now >= expiresAt)")
                .isEmpty();

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
                    decisionService.getLatest(oldId);
                    Decision d = decisionService.recompute(oldId, RECOMPUTE_REQUEST);
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

        long countByRequest = decisionRepository.countByRequestNo(RECOMPUTE_REQUEST);
        assertThat(countByRequest).isEqualTo(1);

        Decision computed = decisionRepository.findByRequestNo(RECOMPUTE_REQUEST).orElseThrow();
        assertThat(computed.getSnapshotId()).isEqualTo(oldId);
        assertThat(computed.getLevel()).isEqualTo(DecisionLevel.ORANGE);
        assertThat(computed.getComputedLevel()).isEqualTo(DecisionLevel.ORANGE);
        assertThat(computed.getActiveOverrideId()).isNull();
        assertThat(computed.getSequenceNo()).isEqualTo(redBefore.getSequenceNo() + 1);

        List<Decision> oldHistory = decisionRepository.findBySnapshotIdOrderBySequenceNoDesc(oldId);
        assertThat(oldHistory).hasSize(2);
        assertThat(oldHistory).extracting(Decision::getLevel)
                .containsExactly(DecisionLevel.ORANGE, DecisionLevel.RED);

        List<NotificationOutbox> computedOutbox =
                outboxRepository.findByDecisionIdOrderByIdAsc(computed.getId());
        assertThat(computedOutbox).hasSize(3);
        assertThat(computedOutbox).allMatch(o -> o.getDecisionId().equals(computed.getId()));
        assertThat(computedOutbox).allMatch(o -> o.getPayload().requestNo().equals(RECOMPUTE_REQUEST));
        assertThat(computedOutbox).extracting(NotificationOutbox::getChannel)
                .containsExactlyInAnyOrder(
                        com.example.basin.evacuation.domain.notification.NotificationChannel.SMS,
                        com.example.basin.evacuation.domain.notification.NotificationChannel.BROADCAST,
                        com.example.basin.evacuation.domain.notification.NotificationChannel.PLATFORM);

        assertThat(overrideRepository.count()).isEqualTo(overrideCountBefore);
        ManualOverride stillPresent = overrideRepository.findById(override.getId()).orElseThrow();
        assertThat(stillPresent.getId()).isEqualTo(override.getId());

        List<NotificationOutbox> oldRedOutboxAfter =
                outboxRepository.findByDecisionIdOrderByIdAsc(redBefore.getId());
        assertThat(oldRedOutboxAfter).hasSize((int) oldRedOutboxCount);
        assertThat(oldRedOutboxAfter).allMatch(o -> oldRedOutboxBefore.stream()
                .anyMatch(before -> before.getId().equals(o.getId())));

        Decision newLatest = decisionService.getLatest(newId).orElseThrow();
        assertThat(newLatest.getId()).isEqualTo(newDecisionId);
        assertThat(newLatest.getLevel()).isEqualTo(DecisionLevel.RED);

        assertThat(outboxRepository.count()).isEqualTo(totalOutboxBefore + 3);
    }
}
