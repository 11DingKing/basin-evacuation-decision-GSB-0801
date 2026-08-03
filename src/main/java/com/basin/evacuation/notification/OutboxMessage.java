package com.basin.evacuation.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 通知 outbox：与决策在同一事务写入，投递状态单独流转（PENDING/FAILED -> SENT），
 * 发送失败可完整重放；数据库触发器禁止 DELETE。
 */
@Entity
@Table(name = "notification_outbox")
public class OutboxMessage {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "decision_id", nullable = false, updatable = false)
    private UUID decisionId;

    @Column(name = "channel", nullable = false, length = 32, updatable = false)
    private String channel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutboxMessage() {}

    public OutboxMessage(UUID id, UUID decisionId, String channel, Map<String, Object> payload, Instant createdAt) {
        this.id = id;
        this.decisionId = decisionId;
        this.channel = channel;
        // 允许 null 值（如无业务请求号时的 requestId），Map.copyOf 会拒绝 null
        this.payload = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(payload));
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.createdAt = createdAt;
    }

    public void markSent(Instant at) {
        this.attempts++;
        this.status = OutboxStatus.SENT;
        this.sentAt = at;
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.attempts++;
        this.status = OutboxStatus.FAILED;
        this.lastError = error == null ? "unknown" : (error.length() > 1000 ? error.substring(0, 1000) : error);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDecisionId() {
        return decisionId;
    }

    public String getChannel() {
        return channel;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
