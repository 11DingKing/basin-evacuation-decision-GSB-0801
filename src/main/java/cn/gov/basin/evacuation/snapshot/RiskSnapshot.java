package cn.gov.basin.evacuation.snapshot;

import cn.gov.basin.evacuation.domain.snapshot.HazardStatus;
import cn.gov.basin.evacuation.domain.snapshot.RiskSnapshotView;
import cn.gov.basin.evacuation.domain.snapshot.RoadStatus;
import cn.gov.basin.evacuation.domain.snapshot.UpstreamHealth;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "risk_snapshots")
public class RiskSnapshot implements RiskSnapshotView {

    @Id
    @Column(length = 64)
    private String snapshotId;

    @Column(name = "region_code", nullable = false, length = 12, updatable = false)
    private String regionCode;

    @Column(name = "observed_at", nullable = false, updatable = false)
    private Instant observedAt;

    @Column(name = "rainfall_3h_mm", precision = 7, scale = 2, updatable = false)
    private BigDecimal rainfall3hMm;

    @Column(name = "water_level_m", precision = 6, scale = 2, updatable = false)
    private BigDecimal waterLevelM;

    @Enumerated(EnumType.STRING)
    @Column(name = "hazard_status", length = 16, updatable = false)
    private HazardStatus hazardStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_road_status", length = 16, updatable = false)
    private RoadStatus primaryRoadStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_road_status", length = 16, updatable = false)
    private RoadStatus secondaryRoadStatus;

    @Column(name = "vulnerable_population", updatable = false)
    private Integer vulnerablePopulation;

    @Enumerated(EnumType.STRING)
    @Column(name = "rainfall_health", nullable = false, length = 16, updatable = false)
    private UpstreamHealth rainfallHealth;

    @Enumerated(EnumType.STRING)
    @Column(name = "water_level_health", nullable = false, length = 16, updatable = false)
    private UpstreamHealth waterLevelHealth;

    @Enumerated(EnumType.STRING)
    @Column(name = "hazard_health", nullable = false, length = 16, updatable = false)
    private UpstreamHealth hazardHealth;

    @Enumerated(EnumType.STRING)
    @Column(name = "infrastructure_health", nullable = false, length = 16, updatable = false)
    private UpstreamHealth infrastructureHealth;

    @Column(name = "rainfall_version", length = 64, updatable = false)
    private String rainfallVersion;

    @Column(name = "water_level_version", length = 64, updatable = false)
    private String waterLevelVersion;

    @Column(name = "hazard_version", length = 64, updatable = false)
    private String hazardVersion;

    @Column(name = "infrastructure_version", length = 64, updatable = false)
    private String infrastructureVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RiskSnapshot() {
    }

    public RiskSnapshot(String snapshotId, String regionCode, Instant observedAt,
                        BigDecimal rainfall3hMm, BigDecimal waterLevelM,
                        HazardStatus hazardStatus,
                        RoadStatus primaryRoadStatus, RoadStatus secondaryRoadStatus,
                        Integer vulnerablePopulation,
                        UpstreamHealth rainfallHealth, UpstreamHealth waterLevelHealth,
                        UpstreamHealth hazardHealth, UpstreamHealth infrastructureHealth,
                        String rainfallVersion, String waterLevelVersion,
                        String hazardVersion, String infrastructureVersion,
                        Instant createdAt) {
        this.snapshotId = snapshotId;
        this.regionCode = regionCode;
        this.observedAt = observedAt;
        this.rainfall3hMm = rainfall3hMm;
        this.waterLevelM = waterLevelM;
        this.hazardStatus = hazardStatus;
        this.primaryRoadStatus = primaryRoadStatus;
        this.secondaryRoadStatus = secondaryRoadStatus;
        this.vulnerablePopulation = vulnerablePopulation;
        this.rainfallHealth = rainfallHealth;
        this.waterLevelHealth = waterLevelHealth;
        this.hazardHealth = hazardHealth;
        this.infrastructureHealth = infrastructureHealth;
        this.rainfallVersion = rainfallVersion;
        this.waterLevelVersion = waterLevelVersion;
        this.hazardVersion = hazardVersion;
        this.infrastructureVersion = infrastructureVersion;
        this.createdAt = createdAt;
    }

    public String getSnapshotId() { return snapshotId; }
    public String getRegionCode() { return regionCode; }
    public Instant getObservedAt() { return observedAt; }
    public BigDecimal getRainfall3hMm() { return rainfall3hMm; }
    public BigDecimal getWaterLevelM() { return waterLevelM; }
    public HazardStatus getHazardStatus() { return hazardStatus; }
    public RoadStatus getPrimaryRoadStatus() { return primaryRoadStatus; }
    public RoadStatus getSecondaryRoadStatus() { return secondaryRoadStatus; }
    public Integer getVulnerablePopulation() { return vulnerablePopulation; }
    public UpstreamHealth getRainfallHealth() { return rainfallHealth; }
    public UpstreamHealth getWaterLevelHealth() { return waterLevelHealth; }
    public UpstreamHealth getHazardHealth() { return hazardHealth; }
    public UpstreamHealth getInfrastructureHealth() { return infrastructureHealth; }
    public String getRainfallVersion() { return rainfallVersion; }
    public String getWaterLevelVersion() { return waterLevelVersion; }
    public String getHazardVersion() { return hazardVersion; }
    public String getInfrastructureVersion() { return infrastructureVersion; }
    public Instant getCreatedAt() { return createdAt; }
}
