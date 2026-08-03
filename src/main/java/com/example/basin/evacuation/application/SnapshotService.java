package com.example.basin.evacuation.application;

import com.example.basin.evacuation.domain.district.AdministrativeDistrict;
import com.example.basin.evacuation.domain.district.DistrictRepository;
import com.example.basin.evacuation.domain.snapshot.RiskSnapshot;
import com.example.basin.evacuation.domain.snapshot.SnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class SnapshotService {

    private final SnapshotRepository snapshotRepository;
    private final DistrictRepository districtRepository;
    private final Clock clock;

    public SnapshotService(SnapshotRepository snapshotRepository,
                           DistrictRepository districtRepository,
                           Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.districtRepository = districtRepository;
        this.clock = clock;
    }

    @Transactional
    public RiskSnapshot ingest(RiskSnapshot snapshot) {
        AdministrativeDistrict district = districtRepository.findById(snapshot.getDistrictCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "district not found: " + snapshot.getDistrictCode()));
        if (snapshotRepository.existsById(snapshot.getSnapshotId())) {
            throw new IllegalArgumentException("snapshot already exists (immutable): " + snapshot.getSnapshotId());
        }
        RiskSnapshot toSave = snapshot.getCreatedAt() == null
                ? withCreatedAt(snapshot)
                : snapshot;
        return snapshotRepository.save(toSave);
    }

    @Transactional(readOnly = true)
    public RiskSnapshot get(String snapshotId) {
        return snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("snapshot not found: " + snapshotId));
    }

    @Transactional(readOnly = true)
    public List<RiskSnapshot> listByDistrict(String districtCode) {
        return snapshotRepository.findByDistrictCodeOrderByObservedAtDesc(districtCode);
    }

    private RiskSnapshot withCreatedAt(RiskSnapshot snapshot) {
        return RiskSnapshot.builder()
                .snapshotId(snapshot.getSnapshotId())
                .districtCode(snapshot.getDistrictCode())
                .observedAt(snapshot.getObservedAt())
                .rainfall3hMm(snapshot.getRainfall3hMm())
                .waterLevelM(snapshot.getWaterLevelM())
                .hazardStatus(snapshot.getHazardStatus())
                .mainRoadStatus(snapshot.getMainRoadStatus())
                .secondaryRoadStatus(snapshot.getSecondaryRoadStatus())
                .vulnerablePopulation(snapshot.getVulnerablePopulation())
                .rainfallHealth(snapshot.getRainfallHealth())
                .waterLevelHealth(snapshot.getWaterLevelHealth())
                .hazardHealth(snapshot.getHazardHealth())
                .roadHealth(snapshot.getRoadHealth())
                .rainfallVersion(snapshot.getRainfallVersion())
                .waterLevelVersion(snapshot.getWaterLevelVersion())
                .hazardVersion(snapshot.getHazardVersion())
                .roadVersion(snapshot.getRoadVersion())
                .evidenceVersion(snapshot.getEvidenceVersion())
                .createdAt(clock.instant())
                .build();
    }
}
