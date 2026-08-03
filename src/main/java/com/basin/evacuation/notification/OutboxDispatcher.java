package com.basin.evacuation.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * outbox 投递器：对 PENDING/FAILED 的消息逐条重试。
 * 每条消息独立 try/catch —— 单条失败不影响其它消息，
 * 且失败会落库为 FAILED + lastError，可无限次重放。
 */
@Service
public class OutboxDispatcher {

    private final OutboxRepository outbox;
    private final NotificationSender sender;
    private final Clock clock;

    public OutboxDispatcher(OutboxRepository outbox, NotificationSender sender, Clock clock) {
        this.outbox = outbox;
        this.sender = sender;
        this.clock = clock;
    }

    public record DispatchResult(int total, int sent, int failed) {}

    @Transactional
    public DispatchResult dispatchPending(int limit) {
        List<OutboxMessage> batch = outbox.findByStatusInOrderByCreatedAtAsc(
                List.of(OutboxStatus.PENDING, OutboxStatus.FAILED), Limit.of(limit));
        int sent = 0;
        int failed = 0;
        for (OutboxMessage message : batch) {
            try {
                sender.send(message);
                message.markSent(Instant.now(clock));
                sent++;
            } catch (Exception e) {
                message.markFailed(e.getMessage());
                failed++;
            }
        }
        return new DispatchResult(batch.size(), sent, failed);
    }
}
