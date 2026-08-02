package cn.gov.basin.evacuation.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "advice_id", nullable = false, updatable = false)
    private Long adviceId;

    @Column(name = "region_code", nullable = false, length = 12, updatable = false)
    private String regionCode;

    @Column(nullable = false, length = 32, updatable = false)
    private String channel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxStatus status;

    @Column(nullable = false)
    private Integer attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "request_id", length = 64, updatable = false)
    private String requestId;

    @Version
    private Long version;

    protected NotificationOutbox() {
    }

    public NotificationOutbox(Long adviceId, String regionCode, String channel,
                               Map<String, Object> payload, Instant now,
                               String requestId) {
        this.adviceId = adviceId;
        this.regionCode = regionCode;
        this.channel = channel;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.createdAt = now;
        this.updatedAt = now;
        this.nextRetryAt = now;
        this.requestId = requestId;
    }

    public void markSent(Instant now) {
        this.status = OutboxStatus.SENT;
        this.sentAt = now;
        this.updatedAt = now;
        this.lastError = null;
    }

    public void markFailure(String error, Instant nextRetry, Instant now) {
        this.status = OutboxStatus.FAILED;
        this.lastError = truncate(error);
        this.nextRetryAt = nextRetry;
        this.updatedAt = now;
    }

    public void incrementAttempts() {
        this.attempts = this.attempts + 1;
    }

    private String truncate(String error) {
        if (error == null) return null;
        return error.length() > 1000 ? error.substring(0, 1000) : error;
    }

    public Long getId() { return id; }
    public Long getAdviceId() { return adviceId; }
    public String getRegionCode() { return regionCode; }
    public String getChannel() { return channel; }
    public Map<String, Object> getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public Integer getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getRequestId() { return requestId; }
}
