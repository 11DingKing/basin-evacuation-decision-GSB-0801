package gov.basin.evac.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import gov.basin.evac.domain.entity.RiskSnapshot;
import gov.basin.evac.domain.entity.SnapshotUpstreamHealth;

/** API view of an immutable risk snapshot including per-feed upstream health. */
public record SnapshotResponse(
        String snapshotId,
        String regionCode,
        long evidenceVersion,
        Instant observedAt,
        BigDecimal rainfall3hMm,
        BigDecimal riverLevelM,
        String hazardPointStatus,
        String primaryRoadStatus,
        String secondaryRoadStatus,
        int vulnerablePopulationPersons,
        String populationUnit,
        List<UpstreamHealthView> upstreamHealth) {

    public record UpstreamHealthView(String source, String status, String reportedVersion, Instant observedAt) {
        static UpstreamHealthView from(SnapshotUpstreamHealth h) {
            return new UpstreamHealthView(h.getSource().name(), h.getStatus().name(),
                    h.getReportedVersion(), h.getObservedAt());
        }
    }

    public static SnapshotResponse from(RiskSnapshot s) {
        return new SnapshotResponse(
                s.getSnapshotId(), s.getRegionCode(), s.getEvidenceVersion(), s.getObservedAt(),
                s.getRainfall3hMm(), s.getRiverLevelM(), s.getHazardPointStatus().name(),
                s.getPrimaryRoadStatus().name(), s.getSecondaryRoadStatus().name(),
                s.getVulnerablePopulationPersons(), "persons",
                s.getUpstreamHealth().stream().map(UpstreamHealthView::from).toList());
    }
}
