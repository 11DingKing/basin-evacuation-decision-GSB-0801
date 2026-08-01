package com.basin.evacuation.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 决策建议：追加式历史记录，数据库触发器禁止 UPDATE/DELETE。
 * 同一快照内 seq 单调递增（唯一约束保证并发安全）。
 */
@Entity
@Table(name = "decision", uniqueConstraints = @UniqueConstraint(
        name = "uq_decision_snapshot_seq", columnNames = {"snapshot_id", "seq"}))
public class Decision {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "snapshot_id", nullable = false, length = 64, updatable = false)
    private String snapshotId;

    /** 引用的证据（快照）版本 */
    @Column(name = "snapshot_version", nullable = false, updatable = false)
    private int snapshotVersion;

    @Column(name = "region_code", nullable = false, length = 12, updatable = false)
    private String regionCode;

    @Column(name = "seq", nullable = false, updatable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 32, updatable = false)
    private DecisionOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16, updatable = false)
    private DecisionSource source;

    @Column(name = "override_id", updatable = false)
    private UUID overrideId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reasons", nullable = false, columnDefinition = "jsonb", updatable = false)
    private List<String> reasons;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", nullable = false, columnDefinition = "jsonb", updatable = false)
    private DecisionEvidence evidence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Decision() {}

    public Decision(UUID id, String snapshotId, int snapshotVersion, String regionCode, int seq,
                    DecisionOutcome outcome, DecisionSource source, UUID overrideId,
                    List<String> reasons, DecisionEvidence evidence, Instant createdAt) {
        this.id = id;
        this.snapshotId = snapshotId;
        this.snapshotVersion = snapshotVersion;
        this.regionCode = regionCode;
        this.seq = seq;
        this.outcome = outcome;
        this.source = source;
        this.overrideId = overrideId;
        this.reasons = List.copyOf(reasons);
        this.evidence = evidence;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public int getSnapshotVersion() {
        return snapshotVersion;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public int getSeq() {
        return seq;
    }

    public DecisionOutcome getOutcome() {
        return outcome;
    }

    public DecisionSource getSource() {
        return source;
    }

    public UUID getOverrideId() {
        return overrideId;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public DecisionEvidence getEvidence() {
        return evidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
