package com.basin.evacuation.notification;

import com.basin.evacuation.decision.Decision;
import com.basin.evacuation.snapshot.RiskSnapshot;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 通知生成（独立领域模块）：把决策渲染成值班通知 payload。
 * 每条通知都携带证据版本（snapshotId + snapshotVersion）。
 */
@Component
public class NotificationGenerator {

    public static final String CHANNEL_DUTY_BROADCAST = "DUTY_BROADCAST";

    public OutboxMessage toOutbox(Decision d, RiskSnapshot s, Instant createdAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "[" + d.getOutcome().label() + "] " + s.getRegionCode() + " 流域转移建议");
        payload.put("body", String.join("；", d.getReasons()));
        payload.put("decisionId", d.getId().toString());
        payload.put("snapshotId", s.getSnapshotId());
        payload.put("snapshotVersion", s.getVersion());
        payload.put("outcome", d.getOutcome().name());
        payload.put("regionCode", s.getRegionCode());
        payload.put("vulnerablePopulation", s.getVulnerablePopulation());
        return new OutboxMessage(UUID.randomUUID(), d.getId(), CHANNEL_DUTY_BROADCAST, payload, createdAt);
    }
}
