package com.example.basin.evacuation.api.dto;

import com.example.basin.evacuation.domain.shared.HazardStatus;
import com.example.basin.evacuation.domain.shared.HealthStatus;
import com.example.basin.evacuation.domain.shared.RoadStatus;
import com.example.basin.evacuation.domain.snapshot.RiskSnapshot;

import java.math.BigDecimal;
import java.time.Instant;

public record SnapshotResponse(
        String snapshotId,
        String districtCode,
        Instant observedAt,
        BigDecimal rainfall3hMm,
        BigDecimal waterLevelM,
        HazardStatus hazardStatus,
        RoadStatus mainRoadStatus,
        RoadStatus secondaryRoadStatus,
        long vulnerablePopulation,
        String populationUnit,
        HealthStatus rainfallHealth,
        HealthStatus waterLevelHealth,
        HealthStatus hazardHealth,
        HealthStatus roadHealth,
        String rainfallVersion,
        String waterLevelVersion,
        String hazardVersion,
        String roadVersion,
        String evidenceVersion,
        Instant createdAt
) {
    public static SnapshotResponse from(RiskSnapshot s) {
        return new SnapshotResponse(
                s.getSnapshotId(),
                s.getDistrictCode(),
                s.getObservedAt(),
                s.getRainfall3hMm(),
                s.getWaterLevelM(),
                s.getHazardStatus(),
                s.getMainRoadStatus(),
                s.getSecondaryRoadStatus(),
                s.getVulnerablePopulation(),
                "人",
                s.getRainfallHealth(),
                s.getWaterLevelHealth(),
                s.getHazardHealth(),
                s.getRoadHealth(),
                s.getRainfallVersion(),
                s.getWaterLevelVersion(),
                s.getHazardVersion(),
                s.getRoadVersion(),
                s.getEvidenceVersion(),
                s.getCreatedAt());
    }
}
