package com.basin.evacuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import com.basin.evacuation.decision.Decision;
import com.basin.evacuation.decision.DecisionService;
import com.basin.evacuation.notification.NotificationSender;
import com.basin.evacuation.notification.OutboxDispatcher;
import com.basin.evacuation.notification.OutboxMessage;
import com.basin.evacuation.notification.OutboxRepository;
import com.basin.evacuation.notification.OutboxStatus;
import com.basin.evacuation.snapshot.HazardPointStatus;
import com.basin.evacuation.snapshot.RoadStatus;
import com.basin.evacuation.snapshot.SnapshotService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 通知发送失败后的完整重放：
 * 1) 发送器抛异常 -> outbox 置 FAILED + lastError，决策与证据原样保留；
 * 2) 发送器恢复后重放 -> 同一条消息置 SENT，不产生重复消息。
 */
class OutboxReplayTest extends PostgresIntegrationTest {

    @Autowired
    SnapshotService snapshotService;
    @Autowired
    DecisionService decisionService;
    @Autowired
    OutboxDispatcher dispatcher;
    @Autowired
    OutboxRepository outboxRepository;

    @MockitoBean
    NotificationSender notificationSender;

    @Test
    void failedNotificationIsReplayableWithoutDuplicates() {
        String snapshotId = "t-outbox-replay-1";
        snapshotService.create(snapshotId, "510182", 1,
                new BigDecimal("60.00"), new BigDecimal("5.600"),
                HazardPointStatus.WARNING, RoadStatus.OPEN, RoadStatus.OPEN,
                42, allOkUpstream(), Instant.parse("2026-08-01T01:30:00Z"));

        Decision decision = decisionService.current(snapshotId);
        assertThat(decision.getOutcome().name()).isEqualTo("PRE_TRANSFER");

        // 第一次投递：发送失败
        doThrow(new RuntimeException("通知网关不可用")).when(notificationSender).send(any());
        var first = dispatcher.dispatchPending(50);
        assertThat(first.failed()).isGreaterThanOrEqualTo(1);

        OutboxMessage failed = outboxRepository.findAll().stream()
                .filter(m -> m.getDecisionId().equals(decision.getId()))
                .findFirst().orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failed.getLastError()).contains("通知网关不可用");
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getSentAt()).isNull();

        // 失败期间决策与证据完好（不是写了一半的状态）
        Decision reloaded = decisionService.get(decision.getId());
        assertThat(reloaded.getReasons()).isNotEmpty();
        assertThat(reloaded.getEvidence().snapshotId()).isEqualTo(snapshotId);
        assertThat(reloaded.getEvidence().snapshotVersion()).isEqualTo(1);
        assertThat(reloaded.getEvidence().vulnerablePopulation()).isEqualTo(42);

        // 发送器恢复：重放 -> SENT，消息不重复
        doNothing().when(notificationSender).send(any());
        var second = dispatcher.dispatchPending(50);
        assertThat(second.sent()).isGreaterThanOrEqualTo(1);

        List<OutboxMessage> messages = outboxRepository.findAll().stream()
                .filter(m -> m.getDecisionId().equals(decision.getId()))
                .toList();
        assertThat(messages).hasSize(1);
        OutboxMessage sent = messages.get(0);
        assertThat(sent.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(sent.getAttempts()).isEqualTo(2);
        assertThat(sent.getSentAt()).isNotNull();
        assertThat(sent.getLastError()).isNull();
        assertThat(sent.getPayload().get("snapshotId")).isEqualTo(snapshotId);
    }
}
