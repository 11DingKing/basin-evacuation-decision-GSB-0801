package cn.gov.basin.evacuation.notification;

import cn.gov.basin.evacuation.AbstractIntegrationTest;
import cn.gov.basin.evacuation.FailingNotificationPortConfig;
import cn.gov.basin.evacuation.TestSnapshots;
import cn.gov.basin.evacuation.decision.AdviceSource;
import cn.gov.basin.evacuation.decision.DecisionAdvice;
import cn.gov.basin.evacuation.decision.DecisionAdviceRepository;
import cn.gov.basin.evacuation.decision.DecisionService;
import cn.gov.basin.evacuation.domain.decision.DecisionLevel;
import cn.gov.basin.evacuation.domain.snapshot.HazardStatus;
import cn.gov.basin.evacuation.domain.snapshot.RoadStatus;
import cn.gov.basin.evacuation.domain.snapshot.UpstreamHealth;
import cn.gov.basin.evacuation.snapshot.RiskSnapshot;
import cn.gov.basin.evacuation.snapshot.SnapshotService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({AbstractIntegrationTest.TestClockConfig.class, FailingNotificationPortConfig.class})
class UpstreamAndOutboxResilienceTest extends AbstractIntegrationTest {

    @Autowired SnapshotService snapshotService;
    @Autowired DecisionService decisionService;
    @Autowired DecisionAdviceRepository adviceRepository;
    @Autowired OutboxRepository outboxRepository;
    @Autowired NotificationService notificationService;
    @Autowired LoggingNotificationSender sender;

    @Test
    @DisplayName("两类上游超时 + 一类旧版本：决策必须为 INSUFFICIENT_DATA，证据保留各版本与健康状态")
    void twoTimeoutsOneStaleYieldsInsufficient() {
        RiskSnapshot s = TestSnapshots.degraded(
                snapshotService,
                "snap-degraded-1",
                "510182",
                UpstreamHealth.TIMEOUT,
                UpstreamHealth.TIMEOUT,
                UpstreamHealth.STALE,
                UpstreamHealth.OK,
                HazardStatus.NORMAL,
                RoadStatus.OPEN
        );

        DecisionAdvice advice = decisionService.computeForSnapshot(s.getSnapshotId());

        assertThat(advice.getDecisionLevel()).isEqualTo(DecisionLevel.INSUFFICIENT_DATA);
        assertThat(advice.getSource()).isEqualTo(AdviceSource.COMPUTED);

        Map<?, ?> evidence = advice.getEvidenceRef();
        Map<?, ?> rain = (Map<?, ?>) evidence.get("rainfall");
        Map<?, ?> water = (Map<?, ?>) evidence.get("waterLevel");
        Map<?, ?> hazard = (Map<?, ?>) evidence.get("hazard");
        assertThat(rain.get("health")).isEqualTo("TIMEOUT");
        assertThat(water.get("health")).isEqualTo("TIMEOUT");
        assertThat(hazard.get("health")).isEqualTo("STALE");
        assertThat(hazard.get("version")).isEqualTo("haz-old");
    }

    @Test
    @DisplayName("通知发送失败：决策与 outbox 同时提交，状态为 FAILED 可重试，无半成功状态")
    void notificationFailureDoesNotCorruptAdvice() {
        RiskSnapshot s = TestSnapshots.degraded(
                snapshotService,
                "snap-fail-notify",
                "510182",
                UpstreamHealth.OK,
                UpstreamHealth.OK,
                UpstreamHealth.OK,
                UpstreamHealth.OK,
                HazardStatus.WARNING,
                RoadStatus.OPEN
        );

        AtomicInteger calls = new AtomicInteger();
        sender.setDelegate((channel, payload) -> {
            calls.incrementAndGet();
            throw new NotificationSendException("simulated downstream SMS gateway timeout");
        });

        DecisionAdvice advice = decisionService.computeForSnapshot(s.getSnapshotId());
        assertThat(adviceRepository.findById(advice.getId())).isPresent();

        NotificationOutbox box = outboxRepository.findAll().stream()
                .filter(o -> o.getAdviceId().equals(advice.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(box.getStatus()).isEqualTo(OutboxStatus.PENDING);

        notificationService.processDue();
        NotificationOutbox after = outboxRepository.findById(box.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(after.getAttempts()).isEqualTo(1);
        assertThat(after.getLastError()).contains("simulated downstream");
        assertThat(after.getNextRetryAt()).isNotNull();

        sender.setDelegate((c, p) -> { });
        tickSeconds(64);
        notificationService.processDue();

        NotificationOutbox retried = outboxRepository.findById(box.getId()).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(retried.getSentAt()).isNotNull();

        sender.reset();
    }

    @Test
    @DisplayName("通知 outbox 必须与决策同事务：模拟 enqueue 阶段抛异常时，决策也必须回滚")
    void atomicityWhenEnqueueFails() {
        RiskSnapshot s = TestSnapshots.degraded(
                snapshotService,
                "snap-atomic",
                "510182",
                UpstreamHealth.OK,
                UpstreamHealth.OK,
                UpstreamHealth.OK,
                UpstreamHealth.OK,
                HazardStatus.NORMAL,
                RoadStatus.OPEN
        );

        long beforeAdvices = adviceRepository.count();
        long beforeOutbox = outboxRepository.count();

        FailingNotificationPortConfig.FAIL_NEXT_ENQUEUE.set(true);
        try {
            assertThatThrownBy(() -> decisionService.computeForSnapshot(s.getSnapshotId()))
                    .isNotNull();
        } finally {
            FailingNotificationPortConfig.FAIL_NEXT_ENQUEUE.set(false);
        }

        assertThat(adviceRepository.count()).isEqualTo(beforeAdvices);
        assertThat(outboxRepository.count()).isEqualTo(beforeOutbox);
    }
}
