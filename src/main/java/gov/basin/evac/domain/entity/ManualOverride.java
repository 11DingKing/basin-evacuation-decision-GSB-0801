package gov.basin.evac.domain.entity;

import java.time.Instant;

import gov.basin.evac.domain.model.DecisionLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An immutable manual override. It forces a decision level for a specific snapshot during a
 * bounded validity window [effectiveFrom, expiresAt). It is never edited or deleted; once it
 * expires the service automatically reverts to the computed result. Every override records who
 * (operator), why (reason) and until when (expiresAt).
 */
@Entity
@Table(name = "manual_overrides")
public class ManualOverride {

    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private String snapshotId;

    @Column(name = "region_code", nullable = false, updatable = false)
    private String regionCode;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "forced_level", nullable = false, updatable = false)
    private DecisionLevel forcedLevel;

    @Column(nullable = false, updatable = false)
    private String operator;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private Instant effectiveFrom;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    // Business request number. Optional, but when present it is globally unique (enforced by a
    // partial unique index in V4) so that replaying the same request is idempotent.
    @Column(name = "request_id", length = 64, updatable = false)
    private String requestId;

    protected ManualOverride() {
    }

    public ManualOverride(String snapshotId, String regionCode, DecisionLevel forcedLevel,
                          String operator, String reason, Instant effectiveFrom, Instant expiresAt,
                          String requestId) {
        this.snapshotId = snapshotId;
        this.regionCode = regionCode;
        this.forcedLevel = forcedLevel;
        this.operator = operator;
        this.reason = reason;
        this.effectiveFrom = effectiveFrom;
        this.expiresAt = expiresAt;
        this.requestId = requestId;
    }

    /**
     * True when {@code now} lies within [effectiveFrom, expiresAt). The half-open interval is
     * deliberate: at the exact expiry instant the override is already inactive.
     */
    public boolean isActiveAt(Instant now) {
        return !now.isBefore(effectiveFrom) && now.isBefore(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public DecisionLevel getForcedLevel() {
        return forcedLevel;
    }

    public String getOperator() {
        return operator;
    }

    public String getReason() {
        return reason;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getRequestId() {
        return requestId;
    }
}
