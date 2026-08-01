package gov.basin.evac.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import gov.basin.evac.domain.model.HazardPointStatus;
import gov.basin.evac.domain.model.RoadStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * An immutable risk snapshot: one versioned bundle of upstream evidence for a region at a
 * point in time. Once persisted it is never updated or deleted (enforced by DB triggers in
 * V2 and by the absence of setters here). Its {@code evidenceVersion} is what advisories cite.
 */
@Entity
@Table(name = "risk_snapshots")
public class RiskSnapshot {

    @Id
    @Column(name = "snapshot_id", length = 64, updatable = false)
    private String snapshotId;

    @Column(name = "region_code", nullable = false, updatable = false)
    private String regionCode;

    // Assigned by the DB sequence default; @Generated makes Hibernate re-read it after INSERT
    // so the in-memory entity carries the version without a manual refresh.
    @org.hibernate.annotations.Generated(event = org.hibernate.generator.EventType.INSERT)
    @Column(name = "evidence_version", insertable = false, updatable = false)
    private Long evidenceVersion;

    @Column(name = "observed_at", nullable = false, updatable = false)
    private Instant observedAt;

    @Column(name = "rainfall_3h_mm", nullable = false, updatable = false)
    private BigDecimal rainfall3hMm;

    @Column(name = "river_level_m", nullable = false, updatable = false)
    private BigDecimal riverLevelM;

    @Enumerated(EnumType.STRING)
    @Column(name = "hazard_point_status", nullable = false, updatable = false)
    private HazardPointStatus hazardPointStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_road_status", nullable = false, updatable = false)
    private RoadStatus primaryRoadStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_road_status", nullable = false, updatable = false)
    private RoadStatus secondaryRoadStatus;

    /** Population unit is fixed to persons. */
    @Column(name = "vulnerable_population_persons", nullable = false, updatable = false)
    private int vulnerablePopulationPersons;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = false)
    private List<SnapshotUpstreamHealth> upstreamHealth = new ArrayList<>();

    protected RiskSnapshot() {
    }

    public RiskSnapshot(String snapshotId, String regionCode, Instant observedAt,
                        BigDecimal rainfall3hMm, BigDecimal riverLevelM,
                        HazardPointStatus hazardPointStatus, RoadStatus primaryRoadStatus,
                        RoadStatus secondaryRoadStatus, int vulnerablePopulationPersons) {
        this.snapshotId = snapshotId;
        this.regionCode = regionCode;
        this.observedAt = observedAt;
        this.rainfall3hMm = rainfall3hMm;
        this.riverLevelM = riverLevelM;
        this.hazardPointStatus = hazardPointStatus;
        this.primaryRoadStatus = primaryRoadStatus;
        this.secondaryRoadStatus = secondaryRoadStatus;
        this.vulnerablePopulationPersons = vulnerablePopulationPersons;
    }

    public void addUpstreamHealth(SnapshotUpstreamHealth health) {
        health.attachTo(this);
        this.upstreamHealth.add(health);
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public Long getEvidenceVersion() {
        return evidenceVersion;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public BigDecimal getRainfall3hMm() {
        return rainfall3hMm;
    }

    public BigDecimal getRiverLevelM() {
        return riverLevelM;
    }

    public HazardPointStatus getHazardPointStatus() {
        return hazardPointStatus;
    }

    public RoadStatus getPrimaryRoadStatus() {
        return primaryRoadStatus;
    }

    public RoadStatus getSecondaryRoadStatus() {
        return secondaryRoadStatus;
    }

    public int getVulnerablePopulationPersons() {
        return vulnerablePopulationPersons;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<SnapshotUpstreamHealth> getUpstreamHealth() {
        return upstreamHealth;
    }
}
