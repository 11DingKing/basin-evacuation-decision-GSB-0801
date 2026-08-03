package gov.basin.evac.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import gov.basin.evac.domain.model.HazardPointStatus;
import gov.basin.evac.domain.model.RoadStatus;
import gov.basin.evac.domain.model.UpstreamSource;
import gov.basin.evac.domain.model.UpstreamStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Request to ingest a new immutable risk snapshot. Population is always in persons. */
public record CreateSnapshotRequest(
        @NotBlank String snapshotId,
        @NotBlank String regionCode,
        @NotNull Instant observedAt,
        @NotNull BigDecimal rainfall3hMm,
        @NotNull BigDecimal riverLevelM,
        @NotNull HazardPointStatus hazardPointStatus,
        @NotNull RoadStatus primaryRoadStatus,
        @NotNull RoadStatus secondaryRoadStatus,
        @PositiveOrZero int vulnerablePopulationPersons,
        @NotNull List<UpstreamHealthInput> upstreamHealth) {

    public record UpstreamHealthInput(
            @NotNull UpstreamSource source,
            @NotNull UpstreamStatus status,
            String reportedVersion,
            Instant observedAt) {
    }
}
