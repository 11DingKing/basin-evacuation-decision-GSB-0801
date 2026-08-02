package cn.gov.basin.evacuation.override;

import cn.gov.basin.evacuation.domain.decision.DecisionLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "manual_overrides")
public class ManualOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "region_code", nullable = false, length = 12, updatable = false)
    private String regionCode;

    @Column(nullable = false, length = 100, updatable = false)
    private String operator;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_level", nullable = false, length = 24, updatable = false)
    private DecisionLevel targetLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OverrideStatus status;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private Instant effectiveFrom;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "request_id", length = 64, updatable = false)
    private String requestId;

    @Column(name = "snapshot_id", length = 64, updatable = false)
    private String snapshotId;

    protected ManualOverride() {
    }

    public ManualOverride(String regionCode, String operator, String reason,
                          DecisionLevel targetLevel, OverrideStatus status,
                          Instant effectiveFrom, Instant expiresAt, Instant createdAt,
                          String requestId, String snapshotId) {
        this.regionCode = regionCode;
        this.operator = operator;
        this.reason = reason;
        this.targetLevel = targetLevel;
        this.status = status;
        this.effectiveFrom = effectiveFrom;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.requestId = requestId;
        this.snapshotId = snapshotId;
    }

    public Long getId() { return id; }
    public String getRegionCode() { return regionCode; }
    public String getOperator() { return operator; }
    public String getReason() { return reason; }
    public DecisionLevel getTargetLevel() { return targetLevel; }
    public OverrideStatus getStatus() { return status; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public String getRequestId() { return requestId; }
    public String getSnapshotId() { return snapshotId; }

    public void markExpired() {
        this.status = OverrideStatus.EXPIRED;
    }

    public boolean isActiveAt(Instant now) {
        return status == OverrideStatus.ACTIVE
                && !now.isBefore(effectiveFrom)
                && now.isBefore(expiresAt);
    }
}
