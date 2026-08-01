package gov.basin.evac.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gov.basin.evac.domain.entity.RiskSnapshot;
import gov.basin.evac.domain.entity.SnapshotUpstreamHealth;
import gov.basin.evac.domain.model.HazardPointStatus;
import gov.basin.evac.domain.model.RoadStatus;
import gov.basin.evac.domain.model.UpstreamSource;
import gov.basin.evac.domain.model.UpstreamStatus;
import gov.basin.evac.domain.repository.RegionRepository;
import gov.basin.evac.domain.repository.RiskSnapshotRepository;

/**
 * Ingests immutable risk snapshots (including each upstream feed's health) and triggers the
 * initial recompute so an advisory exists for the new evidence version.
 */
@Service
public class SnapshotService {

    private final RiskSnapshotRepository snapshotRepository;
    private final RegionRepository regionRepository;
    private final RecomputeService recomputeService;

    public SnapshotService(RiskSnapshotRepository snapshotRepository,
                           RegionRepository regionRepository,
                           RecomputeService recomputeService) {
        this.snapshotRepository = snapshotRepository;
        this.regionRepository = regionRepository;
        this.recomputeService = recomputeService;
    }

    public record UpstreamHealthInput(UpstreamSource source, UpstreamStatus status,
                                      String reportedVersion, Instant observedAt) {
    }

    @Transactional
    public RiskSnapshot ingest(String snapshotId, String regionCode, Instant observedAt,
                               BigDecimal rainfall3hMm, BigDecimal riverLevelM,
                               HazardPointStatus hazardPointStatus, RoadStatus primaryRoadStatus,
                               RoadStatus secondaryRoadStatus, int vulnerablePopulationPersons,
                               List<UpstreamHealthInput> upstreamHealth) {
        if (!regionRepository.existsById(regionCode)) {
            throw new IllegalArgumentException("unknown region: " + regionCode);
        }
        if (snapshotRepository.existsById(snapshotId)) {
            throw new IllegalArgumentException("snapshot already exists (snapshots are immutable): " + snapshotId);
        }

        RiskSnapshot snapshot = new RiskSnapshot(snapshotId, regionCode, observedAt,
                rainfall3hMm, riverLevelM, hazardPointStatus, primaryRoadStatus,
                secondaryRoadStatus, vulnerablePopulationPersons);
        for (UpstreamHealthInput h : upstreamHealth) {
            snapshot.addUpstreamHealth(new SnapshotUpstreamHealth(
                    h.source(), h.status(), h.reportedVersion(), h.observedAt()));
        }
        RiskSnapshot saved = snapshotRepository.saveAndFlush(snapshot);

        recomputeService.recompute(snapshotId);
        return saved;
    }

    @Transactional(readOnly = true)
    public RiskSnapshot get(String snapshotId) {
        return snapshotRepository.findByIdWithHealth(snapshotId)
                .orElseThrow(() -> new SnapshotNotFoundException(snapshotId));
    }
}
