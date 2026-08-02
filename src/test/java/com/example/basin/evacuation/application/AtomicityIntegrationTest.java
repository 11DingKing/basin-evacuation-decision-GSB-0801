package com.example.basin.evacuation.application;

import com.example.basin.evacuation.domain.decision.Decision;
import com.example.basin.evacuation.domain.decision.DecisionRepository;
import com.example.basin.evacuation.domain.notification.NotificationService;
import com.example.basin.evacuation.domain.notification.OutboxRepository;
import com.example.basin.evacuation.domain.snapshot.RiskSnapshot;
import com.example.basin.evacuation.support.AbstractIntegrationTest;
import com.example.basin.evacuation.support.SnapshotTestFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AtomicityIntegrationTest extends AbstractIntegrationTest {

    @Autowired DecisionService decisionService;
    @Autowired SnapshotService snapshotService;
    @Autowired DecisionRepository decisionRepository;
    @Autowired OutboxRepository outboxRepository;

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        NotificationService failingNotificationService(OutboxRepository outboxRepository, Clock clock) {
            return new NotificationService(outboxRepository, clock) {
                @Override
                public java.util.List<com.example.basin.evacuation.domain.notification.NotificationOutbox>
                createForDecision(Decision decision, RiskSnapshot snapshot) {
                    throw new IllegalStateException("simulated outbox failure after decision save");
                }
            };
        }
    }

    @Test
    void whenOutboxWriteFails_decisionAndOutboxAreBothRolledBack() {
        String snapshotId = "snap-atomic-" + System.nanoTime();
        snapshotService.ingest(SnapshotTestFactory.healthy(snapshotId).build());

        assertThatThrownBy(() -> decisionService.recompute(snapshotId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated outbox failure");

        List<Decision> history = decisionRepository.findBySnapshotIdOrderBySequenceNoDesc(snapshotId);
        assertThat(history).isEmpty();
        assertThat(outboxRepository.findAll()).noneMatch(o -> snapshotId.equals(o.getSnapshotId()));
    }
}
