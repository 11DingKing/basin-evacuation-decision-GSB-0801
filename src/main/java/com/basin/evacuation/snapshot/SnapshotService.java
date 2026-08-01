package com.basin.evacuation.snapshot;

import com.basin.evacuation.common.BadRequestException;
import com.basin.evacuation.common.ConflictException;
import com.basin.evacuation.common.NotFoundException;
import com.basin.evacuation.decision.Decision;
import com.basin.evacuation.decision.DecisionService;
import com.basin.evacuation.region.RegionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SnapshotService {

    private final RiskSnapshotRepository snapshots;
    private final RegionRepository regions;
    private final DecisionService decisionService;
    private final Clock clock;

    public SnapshotService(RiskSnapshotRepository snapshots,
                           RegionRepository regions,
                           DecisionService decisionService,
                           Clock clock) {
        this.snapshots = snapshots;
        this.regions = regions;
        this.decisionService = decisionService;
        this.clock = clock;
    }

    /**
     * 创建不可变快照，并在同一事务内完成首次阈值评估（建议 + outbox 一起落库）。
     */
    @Transactional
    public RiskSnapshot create(String snapshotId, String regionCode, Integer version,
                               BigDecimal rainfall3hMm, BigDecimal waterLevelM,
                               HazardPointStatus hazardPointStatus,
                               RoadStatus primaryRoadStatus, RoadStatus secondaryRoadStatus,
                               int vulnerablePopulation,
                               Map<UpstreamKind, UpstreamHealth> upstreamHealth,
                               Instant observedAt) {
        if (snapshots.existsById(snapshotId)) {
            throw new ConflictException("快照已存在（不可变，不允许覆盖）: " + snapshotId);
        }
        if (!regions.existsById(regionCode)) {
            throw new BadRequestException("行政区不存在: " + regionCode);
        }
        RiskSnapshot snapshot = new RiskSnapshot(
                snapshotId, regionCode, version == null ? 1 : version,
                rainfall3hMm, waterLevelM,
                hazardPointStatus, primaryRoadStatus, secondaryRoadStatus,
                vulnerablePopulation, upstreamHealth, observedAt, Instant.now(clock));
        snapshots.save(snapshot);
        decisionService.computeAndRecord(snapshotId);
        return snapshot;
    }

    @Transactional(readOnly = true)
    public RiskSnapshot get(String snapshotId) {
        return snapshots.findById(snapshotId)
                .orElseThrow(() -> new NotFoundException("快照不存在: " + snapshotId));
    }

    @Transactional(readOnly = true)
    public Page<RiskSnapshot> search(String regionCode, Pageable pageable) {
        if (regionCode == null || regionCode.isBlank()) {
            return snapshots.findAll(pageable);
        }
        return snapshots.findByRegionCode(regionCode, pageable);
    }

    @Transactional
    public Decision recompute(String snapshotId) {
        return decisionService.recompute(snapshotId);
    }
}
