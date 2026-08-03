package com.basin.evacuation.snapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 不可变风险快照：数据库层通过触发器禁止 UPDATE/DELETE，
 * 实体只暴露构造与读取，不提供任何修改入口。
 */
@Entity
@Table(name = "risk_snapshot")
public class RiskSnapshot {

    @Id
    @Column(name = "snapshot_id", length = 64)
    private String snapshotId;

    @Column(name = "region_code", nullable = false, length = 12, updatable = false)
    private String regionCode;

    @Column(name = "version", nullable = false, updatable = false)
    private int version;

    /** 3 小时累计降水（mm），null 表示上游未提供 */
    @Column(name = "rainfall_3h_mm", updatable = false)
    private BigDecimal rainfall3hMm;

    /** 河道水位（m），null 表示上游未提供 */
    @Column(name = "water_level_m", updatable = false)
    private BigDecimal waterLevelM;

    @Enumerated(EnumType.STRING)
    @Column(name = "hazard_point_status", nullable = false, length = 16, updatable = false)
    private HazardPointStatus hazardPointStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_road_status", nullable = false, length = 16, updatable = false)
    private RoadStatus primaryRoadStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_road_status", nullable = false, length = 16, updatable = false)
    private RoadStatus secondaryRoadStatus;

    /** 脆弱人群数量，单位：人 */
    @Column(name = "vulnerable_population", nullable = false, updatable = false)
    private int vulnerablePopulation;

    /** 四类上游各自的健康状态 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "upstream_health", nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<UpstreamKind, UpstreamHealth> upstreamHealth;

    @Column(name = "observed_at", nullable = false, updatable = false)
    private Instant observedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RiskSnapshot() {}

    public RiskSnapshot(String snapshotId, String regionCode, int version,
                        BigDecimal rainfall3hMm, BigDecimal waterLevelM,
                        HazardPointStatus hazardPointStatus,
                        RoadStatus primaryRoadStatus, RoadStatus secondaryRoadStatus,
                        int vulnerablePopulation,
                        Map<UpstreamKind, UpstreamHealth> upstreamHealth,
                        Instant observedAt, Instant createdAt) {
        this.snapshotId = snapshotId;
        this.regionCode = regionCode;
        this.version = version;
        this.rainfall3hMm = rainfall3hMm;
        this.waterLevelM = waterLevelM;
        this.hazardPointStatus = hazardPointStatus;
        this.primaryRoadStatus = primaryRoadStatus;
        this.secondaryRoadStatus = secondaryRoadStatus;
        this.vulnerablePopulation = vulnerablePopulation;
        this.upstreamHealth = upstreamHealth == null ? Map.of() : Map.copyOf(upstreamHealth);
        this.observedAt = observedAt;
        this.createdAt = createdAt;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public int getVersion() {
        return version;
    }

    public BigDecimal getRainfall3hMm() {
        return rainfall3hMm;
    }

    public BigDecimal getWaterLevelM() {
        return waterLevelM;
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

    public int getVulnerablePopulation() {
        return vulnerablePopulation;
    }

    public Map<UpstreamKind, UpstreamHealth> getUpstreamHealth() {
        return upstreamHealth;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
