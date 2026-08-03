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

    /** replayed=true 表示相同请求号的幂等重放，返回的是既有快照 */
    public record CreateSnapshotResult(RiskSnapshot snapshot, boolean replayed) {}

    /**
     * 创建不可变快照，并在同一事务内完成首次阈值评估（建议 + outbox 一起落库）。
     *
     * 幂等：持行政区行锁串行化并发创建；快照已存在时，
     * 若 requestId 与该快照某条建议的请求号一致则视为重放并返回既有快照（replayed=true），
     * 否则抛出 409。相同请求号永远只产生一条建议与一条通知。
     */
    @Transactional
    public CreateSnapshotResult create(String snapshotId, String regionCode, Integer version,
                                       BigDecimal rainfall3hMm, BigDecimal waterLevelM,
                                       HazardPointStatus hazardPointStatus,
                                       RoadStatus primaryRoadStatus, RoadStatus secondaryRoadStatus,
                                       int vulnerablePopulation,
                                       Map<UpstreamKind, UpstreamHealth> upstreamHealth,
                                       Instant observedAt, String requestId) {
        regions.lockByCode(regionCode)
                .orElseThrow(() -> new BadRequestException("行政区不存在: " + regionCode));
        var existing = snapshots.findById(snapshotId);
        if (existing.isPresent()) {
            if (requestId != null && !requestId.isBlank()
                    && decisionService.findByRequestId(requestId.strip())
                        .filter(d -> d.getSnapshotId().equals(snapshotId))
                        .isPresent()) {
                return new CreateSnapshotResult(existing.get(), true);
            }
            throw new ConflictException("快照已存在（不可变，不允许覆盖）: " + snapshotId);
        }
        RiskSnapshot snapshot = new RiskSnapshot(
                snapshotId, regionCode, version == null ? 1 : version,
                rainfall3hMm, waterLevelM,
                hazardPointStatus, primaryRoadStatus, secondaryRoadStatus,
                vulnerablePopulation, upstreamHealth, observedAt, Instant.now(clock));
        snapshots.save(snapshot);
        decisionService.computeAndRecord(snapshotId, requestId);
        return new CreateSnapshotResult(snapshot, false);
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
    public Decision recompute(String snapshotId, String requestId) {
        return decisionService.recompute(snapshotId, requestId);
    }
}
