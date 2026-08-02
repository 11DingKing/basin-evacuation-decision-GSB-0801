package cn.gov.basin.evacuation.decision;

import cn.gov.basin.evacuation.domain.decision.DecisionLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "decision_advices")
public class DecisionAdvice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false, length = 64, updatable = false)
    private String snapshotId;

    @Column(name = "region_code", nullable = false, length = 12, updatable = false)
    private String regionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_level", nullable = false, length = 24, updatable = false)
    private DecisionLevel decisionLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private AdviceSource source;

    @Column(name = "advice_text", nullable = false, updatable = false)
    private String adviceText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_ref", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> evidenceRef;

    @Column(name = "override_id", updatable = false)
    private Long overrideId;

    @Column(name = "computed_at", nullable = false, updatable = false)
    private Instant computedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "request_id", length = 64, updatable = false)
    private String requestId;

    protected DecisionAdvice() {
    }

    public DecisionAdvice(String snapshotId, String regionCode,
                          DecisionLevel decisionLevel, AdviceSource source,
                          String adviceText, Map<String, Object> evidenceRef,
                          Long overrideId, Instant computedAt, Instant createdAt,
                          String requestId) {
        this.snapshotId = snapshotId;
        this.regionCode = regionCode;
        this.decisionLevel = decisionLevel;
        this.source = source;
        this.adviceText = adviceText;
        this.evidenceRef = evidenceRef;
        this.overrideId = overrideId;
        this.computedAt = computedAt;
        this.createdAt = createdAt;
        this.requestId = requestId;
    }

    public Long getId() { return id; }
    public String getSnapshotId() { return snapshotId; }
    public String getRegionCode() { return regionCode; }
    public DecisionLevel getDecisionLevel() { return decisionLevel; }
    public AdviceSource getSource() { return source; }
    public String getAdviceText() { return adviceText; }
    public Map<String, Object> getEvidenceRef() { return evidenceRef; }
    public Long getOverrideId() { return overrideId; }
    public Instant getComputedAt() { return computedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public String getRequestId() { return requestId; }
}
