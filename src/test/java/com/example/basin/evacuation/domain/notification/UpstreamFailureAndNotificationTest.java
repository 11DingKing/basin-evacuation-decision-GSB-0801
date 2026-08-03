package com.example.basin.evacuation.domain.notification;

import com.example.basin.evacuation.application.DecisionService;
import com.example.basin.evacuation.application.SnapshotService;
import com.example.basin.evacuation.domain.decision.Decision;
import com.example.basin.evacuation.domain.shared.DecisionLevel;
import com.example.basin.evacuation.domain.shared.HealthStatus;
import com.example.basin.evacuation.domain.snapshot.RiskSnapshot;
import com.example.basin.evacuation.support.AbstractIntegrationTest;
import com.example.basin.evacuation.support.SnapshotTestFactory;
import com.example.basin.evacuation.support.TestClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UpstreamFailureAndNotificationTest extends AbstractIntegrationTest {

    @Autowired DecisionService decisionService;
    @Autowired SnapshotService snapshotService;
    @Autowired OutboxRepository outboxRepository;
    @Autowired NotificationRelay relay;
    @Autowired ControllableSender sender;
    @Autowired TestClock clock;

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        ControllableSender controllableSender() {
            return new ControllableSender();
        }
    }

    static class ControllableSender implements NotificationSender {
        volatile boolean fail = false;
        volatile int sentCount = 0;

        @Override
        public void send(NotificationOutbox outbox) {
            if (fail) {
                throw new NotificationDeliveryException("simulated gateway failure");
            }
            sentCount++;
        }
    }

    @Test
    void twoTimeoutsOneStale_notificationFailureThenRecovery_isFullyReplayable() {
        String snapshotId = "snap-degraded-" + System.nanoTime();
        RiskSnapshot snapshot = SnapshotTestFactory.healthy(snapshotId)
                .rainfallHealth(HealthStatus.STALE)
                .rainfall3hMm(new BigDecimal("118.0"))
                .waterLevelHealth(HealthStatus.HEALTHY)
                .waterLevelM(new BigDecimal("6.12"))
                .hazardHealth(HealthStatus.TIMEOUT).hazardStatus(null)
                .roadHealth(HealthStatus.TIMEOUT).mainRoadStatus(null).secondaryRoadStatus(null)
                .build();
        snapshotService.ingest(snapshot);

        sender.fail = true;
        Decision decision = decisionService.recompute(snapshotId);

        assertThat(decision.getLevel()).isEqualTo(DecisionLevel.ORANGE);
        assertThat(decision.getComputedLevel()).isEqualTo(DecisionLevel.ORANGE);
        assertThat(decision.getEvidenceVersion()).isEqualTo(snapshotId);
        assertThat(decision.getDimensionBreakdown().dataSufficient()).isTrue();
        assertThat(decision.getDimensionBreakdown().staleDimensions())
                .extracting(Enum::name).contains("RAINFALL");
        assertThat(decision.getDimensionBreakdown().missingDimensions())
                .extracting(Enum::name).containsExactlyInAnyOrder("HAZARD", "ROAD");

        List<NotificationOutbox> outbox = outboxRepository.findByDecisionIdOrderByIdAsc(decision.getId());
        assertThat(outbox).hasSize(3);
        assertThat(outbox).allMatch(o -> o.getStatus() == NotificationStatus.PENDING);
        assertThat(outbox).allMatch(o -> o.getPayload().level() == DecisionLevel.ORANGE);
        assertThat(outbox).allMatch(o -> o.getPayload().evidenceVersion().equals(snapshotId));

        relay.dispatchPending();

        List<NotificationOutbox> afterFail = outboxRepository.findByDecisionIdOrderByIdAsc(decision.getId());
        assertThat(afterFail).allMatch(o -> o.getStatus() == NotificationStatus.FAILED);
        assertThat(afterFail).allMatch(o -> o.getRetryCount() == 1);
        assertThat(afterFail).allMatch(o -> o.getLastError().contains("simulated gateway failure"));
        Decision unchangedAfterFail = decisionService.getById(decision.getId()).orElseThrow();
        assertThat(unchangedAfterFail.getLevel()).isEqualTo(DecisionLevel.ORANGE);
        assertThat(unchangedAfterFail.getSequenceNo()).isEqualTo(decision.getSequenceNo());

        sender.fail = false;
        clock.advanceMillis(Duration.ofSeconds(11).toMillis());
        relay.dispatchPending();

        List<NotificationOutbox> afterRecover = outboxRepository.findByDecisionIdOrderByIdAsc(decision.getId());
        assertThat(afterRecover).hasSize(3);
        assertThat(afterRecover).allMatch(o -> o.getStatus() == NotificationStatus.SENT);
        assertThat(afterRecover).allMatch(o -> o.getSentAt() != null);
        assertThat(sender.sentCount).isGreaterThanOrEqualTo(3);
        Decision unchangedAfterRecover = decisionService.getById(decision.getId()).orElseThrow();
        assertThat(unchangedAfterRecover.getLevel()).isEqualTo(DecisionLevel.ORANGE);
    }

    @Test
    void bothCoreFeedsDown_yieldsInsufficientDataWithPlatformNotification() {
        String snapshotId = "snap-insufficient-" + System.nanoTime();
        RiskSnapshot snapshot = SnapshotTestFactory.healthy(snapshotId)
                .rainfallHealth(HealthStatus.TIMEOUT).rainfall3hMm(null)
                .waterLevelHealth(HealthStatus.TIMEOUT).waterLevelM(null)
                .hazardHealth(HealthStatus.HEALTHY)
                .roadHealth(HealthStatus.HEALTHY)
                .build();
        snapshotService.ingest(snapshot);

        Decision decision = decisionService.recompute(snapshotId);

        assertThat(decision.getLevel()).isEqualTo(DecisionLevel.INSUFFICIENT_DATA);
        assertThat(decision.getComputedLevel()).isEqualTo(DecisionLevel.INSUFFICIENT_DATA);
        assertThat(decision.getDimensionBreakdown().dataSufficient()).isFalse();

        List<NotificationOutbox> outbox = outboxRepository.findByDecisionIdOrderByIdAsc(decision.getId());
        assertThat(outbox).hasSize(1);
        assertThat(outbox.get(0).getChannel()).isEqualTo(NotificationChannel.PLATFORM);
        assertThat(outbox.get(0).getPayload().level()).isEqualTo(DecisionLevel.INSUFFICIENT_DATA);
    }
}
