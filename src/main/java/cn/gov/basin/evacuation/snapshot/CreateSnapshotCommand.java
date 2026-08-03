package cn.gov.basin.evacuation.snapshot;

import cn.gov.basin.evacuation.domain.snapshot.HazardStatus;
import cn.gov.basin.evacuation.domain.snapshot.RoadStatus;
import cn.gov.basin.evacuation.domain.snapshot.UpstreamHealth;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateSnapshotCommand(
        @NotBlank String snapshotId,
        @NotBlank String regionCode,
        @NotNull Instant observedAt,
        BigDecimal rainfall3hMm,
        BigDecimal waterLevelM,
        HazardStatus hazardStatus,
        RoadStatus primaryRoadStatus,
        RoadStatus secondaryRoadStatus,
        Integer vulnerablePopulation,
        @NotNull UpstreamHealth rainfallHealth,
        @NotNull UpstreamHealth waterLevelHealth,
        @NotNull UpstreamHealth hazardHealth,
        @NotNull UpstreamHealth infrastructureHealth,
        String rainfallVersion,
        String waterLevelVersion,
        String hazardVersion,
        String infrastructureVersion
) {
}
