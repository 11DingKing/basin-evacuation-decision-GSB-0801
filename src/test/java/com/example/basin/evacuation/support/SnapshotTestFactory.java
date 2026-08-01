package com.example.basin.evacuation.support;

import com.example.basin.evacuation.domain.shared.HazardStatus;
import com.example.basin.evacuation.domain.shared.HealthStatus;
import com.example.basin.evacuation.domain.shared.RoadStatus;
import com.example.basin.evacuation.domain.snapshot.RiskSnapshot;

import java.math.BigDecimal;
import java.time.Instant;

public final class SnapshotTestFactory {

    private SnapshotTestFactory() {
    }

    public static RiskSnapshot.RiskSnapshotBuilder healthy(String snapshotId) {
        return RiskSnapshot.builder()
                .snapshotId(snapshotId)
                .districtCode("510182")
                .observedAt(Instant.parse("2026-07-29T03:00:00Z"))
                .rainfall3hMm(new BigDecimal("118.0"))
                .waterLevelM(new BigDecimal("6.12"))
                .hazardStatus(HazardStatus.WARNING)
                .mainRoadStatus(RoadStatus.CLOSED)
                .secondaryRoadStatus(RoadStatus.UNKNOWN)
                .vulnerablePopulation(286)
                .rainfallHealth(HealthStatus.HEALTHY)
                .waterLevelHealth(HealthStatus.HEALTHY)
                .hazardHealth(HealthStatus.HEALTHY)
                .roadHealth(HealthStatus.HEALTHY)
                .rainfallVersion("r1")
                .waterLevelVersion("w1")
                .hazardVersion("h1")
                .roadVersion("rd1")
                .evidenceVersion(snapshotId)
                .createdAt(Instant.parse("2026-07-29T03:00:00Z"));
    }
}
