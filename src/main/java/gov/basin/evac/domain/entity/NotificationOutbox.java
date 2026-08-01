package gov.basin.evac.domain.entity;

import java.time.Instant;

import gov.basin.evac.domain.model.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A transactional outbox record. Written in the SAME transaction as its advisory, so an
 * advisory can never exist without a notification intent (no "looks committed but only half
 * written" state). A separate dispatcher delivers it and records success/failure, enabling
 * safe replay after a send failure or crash.
 */
@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "advisory_id", nullable = false, updatable = false)
    private Long advisoryId;

    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private String snapshotId;

    @Column(name = "evidence_version", nullable = false, updatable = false)
    private long evidenceVersion;

    @Column(nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    // Optimistic lock so concurrent dispatchers cannot double-send the same record.
    @Version
    @Column(name = "version")
    private Long version;

    protected NotificationOutbox() {
    }

    public NotificationOutbox(Long advisoryId, String snapshotId, long evidenceVersion, String payload) {
        this.advisoryId = advisoryId;
        this.snapshotId = snapshotId;
        this.evidenceVersion = evidenceVersion;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
    }

    public void markSent(Instant when) {
        this.status = OutboxStatus.SENT;
        this.sentAt = when;
        this.attempts += 1;
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.status = OutboxStatus.FAILED;
        this.attempts += 1;
        this.lastError = error;
    }

    public Long getId() {
        return id;
    }

    public Long getAdvisoryId() {
        return advisoryId;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public long getEvidenceVersion() {
        return evidenceVersion;
    }

    public String getPayload() {
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
