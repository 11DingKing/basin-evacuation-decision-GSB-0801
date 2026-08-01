package com.example.basin.evacuation.api.controller;

import com.example.basin.evacuation.domain.notification.NotificationOutbox;
import com.example.basin.evacuation.domain.notification.NotificationRelay;
import com.example.basin.evacuation.domain.notification.NotificationStatus;
import com.example.basin.evacuation.domain.notification.OutboxRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Transactional outbox observability and manual relay trigger")
public class NotificationController {

    private final OutboxRepository outboxRepository;
    private final NotificationRelay relay;

    public NotificationController(OutboxRepository outboxRepository, NotificationRelay relay) {
        this.outboxRepository = outboxRepository;
        this.relay = relay;
    }

    @GetMapping("/outbox")
    @Operation(summary = "List recent outbox rows (PENDING/FAILED/SENT) for observability")
    public List<NotificationOutbox> outbox(@RequestParam(defaultValue = "50") int limit) {
        return outboxRepository.findBatchForDispatch(
                NotificationStatus.PENDING,
                NotificationStatus.FAILED,
                Instant.now(),
                PageRequest.of(0, Math.min(limit, 200)));
    }

    @PostMapping("/dispatch")
    @Operation(summary = "Trigger an outbox dispatch pass immediately")
    public int dispatch() {
        return relay.dispatchPending();
    }
}
