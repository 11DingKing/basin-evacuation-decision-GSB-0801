package com.basin.evacuation.override_;

import com.basin.evacuation.decision.DecisionOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 人工覆写：必须包含操作者、理由、过期时间。
 * 数据库触发器禁止 UPDATE/DELETE —— 覆写永远不会被删除，只会到期失效。
 * 生效语义：now < expiresAt 时生效；now >= expiresAt 视为过期，自动恢复计算结果。
 */
@Entity
@Table(name = "manual_override")
public class ManualOverride {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "snapshot_id", nullable = false, length = 64, updatable = false)
    private String snapshotId;

    /** 业务请求号（幂等依据）：相同请求号的重试返回同一条覆写，不再生成新记录 */
    @Column(name = "request_id", length = 64, updatable = false)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 32, updatable = false)
    private DecisionOutcome outcome;

    @Column(name = "operator", nullable = false, length = 64, updatable = false)
    private String operator;

    @Column(name = "reason", nullable = false, length = 512, updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected ManualOverride() {}

    public ManualOverride(UUID id, String snapshotId, String requestId, DecisionOutcome outcome,
                          String operator, String reason, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.snapshotId = snapshotId;
        this.requestId = requestId;
        this.outcome = outcome;
        this.operator = operator;
        this.reason = reason;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isActiveAt(Instant now) {
        return now.isBefore(expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public String getRequestId() {
        return requestId;
    }

    public DecisionOutcome getOutcome() {
        return outcome;
    }

    public String getOperator() {
        return operator;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
