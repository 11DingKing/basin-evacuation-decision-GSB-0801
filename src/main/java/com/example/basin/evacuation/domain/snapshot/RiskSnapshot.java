package com.example.basin.evacuation.domain.snapshot;

import com.example.basin.evacuation.domain.shared.HazardStatus;
import com.example.basin.evacuation.domain.shared.HealthStatus;
import com.example.basin.evacuation.domain.shared.RoadStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "risk_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RiskSnapshot {

    @Id
    @Column(name = "snapshot_id", length = 64)
    private String snapshotId;

    @Column(name = "district_code", nullable = false, length = 12)
    private String districtCode;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "rainfall_3h_mm", precision = 7, scale = 1)
    private BigDecimal rainfall3hMm;

    @Column(name = "water_level_m", precision = 7, scale = 2)
    private BigDecimal waterLevelM;

    @Enumerated(EnumType.STRING)
    @Column(name = "hazard_status", length = 20)
    private HazardStatus hazardStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "main_road_status", length = 20)
    private RoadStatus mainRoadStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_road_status", length = 20)
    private RoadStatus secondaryRoadStatus;

    @Column(name = "vulnerable_population", nullable = false)
    private long vulnerablePopulation;

    @Enumerated(EnumType.STRING)
    @Column(name = "rainfall_health", nullable = false, length = 20)
    private HealthStatus rainfallHealth;

    @Enumerated(EnumType.STRING)
    @Column(name = "water_level_health", nullable = false, length = 20)
    private HealthStatus waterLevelHealth;

    @Enumerated(EnumType.STRING)
    @Column(name = "hazard_health", nullable = false, length = 20)
    private HealthStatus hazardHealth;

    @Enumerated(EnumType.STRING)
    @Column(name = "road_health", nullable = false, length = 20)
    private HealthStatus roadHealth;

    @Column(name = "rainfall_version", length = 40)
    private String rainfallVersion;

    @Column(name = "water_level_version", length = 40)
    private String waterLevelVersion;

    @Column(name = "hazard_version", length = 40)
    private String hazardVersion;

    @Column(name = "road_version", length = 40)
    private String roadVersion;

    @Column(name = "evidence_version", nullable = false, length = 100)
    private String evidenceVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
