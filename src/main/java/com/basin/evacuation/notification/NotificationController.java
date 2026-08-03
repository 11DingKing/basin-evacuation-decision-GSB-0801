package com.basin.evacuation.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "通知 outbox", description = "决策与 outbox 同事务写入；投递失败可完整重放，不出现写了一半的状态")
public class NotificationController {

    private final OutboxRepository outbox;
    private final OutboxDispatcher dispatcher;

    public NotificationController(OutboxRepository outbox, OutboxDispatcher dispatcher) {
        this.outbox = outbox;
        this.dispatcher = dispatcher;
    }

    public record OutboxResponse(
            UUID id, UUID decisionId, String channel, Map<String, Object> payload,
            OutboxStatus status, int attempts, String lastError, Instant createdAt, Instant sentAt) {
        static OutboxResponse from(OutboxMessage m) {
            return new OutboxResponse(m.getId(), m.getDecisionId(), m.getChannel(), m.getPayload(),
                    m.getStatus(), m.getAttempts(), m.getLastError(), m.getCreatedAt(), m.getSentAt());
        }
    }

    @GetMapping
    @Operation(summary = "outbox 检索", description = "status 可取 PENDING/SENT/FAILED；为空时全量分页")
    public Page<OutboxResponse> search(@RequestParam(required = false) OutboxStatus status, Pageable pageable) {
        Page<OutboxMessage> page = status == null ? outbox.findAll(pageable) : outbox.findByStatus(status, pageable);
        return page.map(OutboxResponse::from);
    }

    @PostMapping("/dispatch")
    @Operation(summary = "投递/重放", description = "对 PENDING 与 FAILED 的消息逐条重试；单条失败落库为 FAILED + lastError")
    public OutboxDispatcher.DispatchResult dispatch(@RequestParam(defaultValue = "50") int limit) {
        return dispatcher.dispatchPending(limit);
    }
}
