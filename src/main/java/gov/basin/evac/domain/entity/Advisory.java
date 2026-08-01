package gov.basin.evac.domain.entity;

import java.time.Instant;

import gov.basin.evac.domain.model.AdvisorySource;
import gov.basin.evac.domain.model.DecisionLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A decision advisory: one immutable, historical record of the decision for a snapshot at a
 * given time. Advisories are append-only; a recompute inserts a new row and marks prior ones
 * {@code superseded}. The advisory always cites the {@code evidenceVersion} it was based on so
 * operators can see exactly which evidence produced the recommendation.
 */
@Entity
@Table(name = "advisories")
public class Advisory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private String snapshotId;

    @Column(name = "region_code", nullable = false, updatable = false)
    private String regionCode;

    @Column(name = "evidence_version", nullable = false, updatable = false)
    private long evidenceVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_level", nullable = false, updatable = false)
    private DecisionLevel decisionLevel;

    @Column(nullable = false, updatable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AdvisorySource source;

    @Column(name = "override_id", updatable = false)
    private Long overrideId;

    @Column(nullable = false, updatable = false)
    private String rationale;

    @Column(name = "affected_persons", nullable = false, updatable = false)
    private int affectedPersons;

    @Column(name = "computed_at", nullable = false, updatable = false)
    private Instant computedAt;

    // The only mutable field; flips false -> true when a newer advisory replaces this one.
    @Column(nullable = false)
    private boolean superseded;

    protected Advisory() {
    }

    public Advisory(String snapshotId, String regionCode, long evidenceVersion,
                    DecisionLevel decisionLevel, AdvisorySource source, Long overrideId,
                    String rationale, int affectedPersons, Instant computedAt) {
        this.snapshotId = snapshotId;
        this.regionCode = regionCode;
        this.evidenceVersion = evidenceVersion;
        this.decisionLevel = decisionLevel;
        this.priority = decisionLevel.priority();
        this.source = source;
        this.overrideId = overrideId;
        this.rationale = rationale;
        this.affectedPersons = affectedPersons;
        this.computedAt = computedAt;
        this.superseded = false;
    }

    public void supersede() {
        this.superseded = true;
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

    public long getEvidenceVersion() {
        return evidenceVersion;
    }

    public DecisionLevel getDecisionLevel() {
        return decisionLevel;
    }

    public int getPriority() {
        return priority;
    }

    public AdvisorySource getSource() {
        return source;
    }

    public Long getOverrideId() {
        return overrideId;
    }

    public String getRationale() {
        return rationale;
    }

    public int getAffectedPersons() {
        return affectedPersons;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public boolean isSuperseded() {
        return superseded;
    }
}
