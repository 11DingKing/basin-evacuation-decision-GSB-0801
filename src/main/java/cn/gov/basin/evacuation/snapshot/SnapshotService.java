package cn.gov.basin.evacuation.snapshot;

import cn.gov.basin.evacuation.region.RegionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class SnapshotService {

    private final RiskSnapshotRepository repository;
    private final RegionService regionService;
    private final Clock clock;

    public SnapshotService(RiskSnapshotRepository repository,
                           RegionService regionService,
                           Clock clock) {
        this.repository = repository;
        this.regionService = regionService;
        this.clock = clock;
    }

    @Transactional
    public RiskSnapshot ingest(CreateSnapshotCommand cmd) {
        regionService.get(cmd.regionCode());
        if (repository.existsById(cmd.snapshotId())) {
            throw new SnapshotAlreadyExistsException(cmd.snapshotId());
        }
        RiskSnapshot snapshot = new RiskSnapshot(
                cmd.snapshotId(),
                cmd.regionCode(),
                cmd.observedAt(),
                cmd.rainfall3hMm(),
                cmd.waterLevelM(),
                cmd.hazardStatus(),
                cmd.primaryRoadStatus(),
                cmd.secondaryRoadStatus(),
                cmd.vulnerablePopulation(),
                cmd.rainfallHealth(),
                cmd.waterLevelHealth(),
                cmd.hazardHealth(),
                cmd.infrastructureHealth(),
                cmd.rainfallVersion(),
                cmd.waterLevelVersion(),
                cmd.hazardVersion(),
                cmd.infrastructureVersion(),
                clock.instant()
        );
        return repository.save(snapshot);
    }

    @Transactional(readOnly = true)
    public RiskSnapshot get(String snapshotId) {
        return repository.findById(snapshotId)
                .orElseThrow(() -> new SnapshotNotFoundException(snapshotId));
    }

    @Transactional(readOnly = true)
    public Page<RiskSnapshot> search(String regionCode, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        if (regionCode == null || regionCode.isBlank()) {
            return repository.findAll(pageable);
        }
        return repository.findByRegionCodeOrderByObservedAtDesc(regionCode, pageable);
    }
}
