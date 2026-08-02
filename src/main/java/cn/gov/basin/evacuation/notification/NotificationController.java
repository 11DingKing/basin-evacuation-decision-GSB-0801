package cn.gov.basin.evacuation.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Notifications", description = "通知 outbox 状态")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final OutboxRepository repository;

    public NotificationController(OutboxRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "查看各状态通知数量")
    @GetMapping("/stats")
    public Map<String, Long> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("PENDING", repository.countByStatus(OutboxStatus.PENDING));
        stats.put("FAILED", repository.countByStatus(OutboxStatus.FAILED));
        stats.put("SENT", repository.countByStatus(OutboxStatus.SENT));
        return stats;
    }
}
