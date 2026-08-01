package com.example.basin.evacuation.api.dto;

import com.example.basin.evacuation.domain.shared.HazardStatus;
import com.example.basin.evacuation.domain.shared.HealthStatus;
import com.example.basin.evacuation.domain.shared.RoadStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public record SnapshotRequest(
        @NotBlank String snapshotId,
        @NotBlank String districtCode,
        @NotNull Instant observedAt,
        BigDecimal rainfall3hMm,
        BigDecimal waterLevelM,
        HazardStatus hazardStatus,
        RoadStatus mainRoadStatus,
        RoadStatus secondaryRoadStatus,
        @PositiveOrZero long vulnerablePopulation,
        @NotNull HealthStatus rainfallHealth,
        @NotNull HealthStatus waterLevelHealth,
        @NotNull HealthStatus hazardHealth,
        @NotNull HealthStatus roadHealth,
        String rainfallVersion,
        String waterLevelVersion,
        String hazardVersion,
        String roadVersion,
        @NotBlank String evidenceVersion
) {
}
