package com.basin.evacuation.snapshot;

import com.basin.evacuation.decision.DecisionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/snapshots")
@Tag(name = "风险快照", description = "不可变风险快照：创建即触发首次阈值评估；支持详情、检索与并发安全重算")
public class SnapshotController {

    private final SnapshotService snapshots;
    private final com.basin.evacuation.decision.DecisionService decisions;

    public SnapshotController(SnapshotService snapshots, com.basin.evacuation.decision.DecisionService decisions) {
        this.snapshots = snapshots;
        this.decisions = decisions;
    }

    public record CreateSnapshotRequest(
            @NotBlank String snapshotId,
            @NotBlank String regionCode,
            @Min(1) Integer version,
            BigDecimal rainfall3hMm,
            BigDecimal waterLevelM,
            @NotNull HazardPointStatus hazardPointStatus,
            @NotNull RoadStatus primaryRoadStatus,
            @NotNull RoadStatus secondaryRoadStatus,
            @Min(0) int vulnerablePopulation,
            @NotEmpty Map<UpstreamKind, UpstreamHealth> upstreamHealth,
            @NotNull Instant observedAt) {}

    public record SnapshotResponse(
            String snapshotId, String regionCode, int version,
            BigDecimal rainfall3hMm, BigDecimal waterLevelM,
            HazardPointStatus hazardPointStatus,
            RoadStatus primaryRoadStatus, RoadStatus secondaryRoadStatus,
            int vulnerablePopulation,
            Map<UpstreamKind, UpstreamHealth> upstreamHealth,
            Instant observedAt, Instant createdAt) {
        static SnapshotResponse from(RiskSnapshot s) {
            return new SnapshotResponse(
                    s.getSnapshotId(), s.getRegionCode(), s.getVersion(),
                    s.getRainfall3hMm(), s.getWaterLevelM(),
                    s.getHazardPointStatus(), s.getPrimaryRoadStatus(), s.getSecondaryRoadStatus(),
                    s.getVulnerablePopulation(), s.getUpstreamHealth(), s.getObservedAt(), s.getCreatedAt());
        }
    }

    @PostMapping
    @Operation(summary = "创建快照", description = "同事务生成首条计算建议与通知 outbox；snapshotId 已存在返回 409")
    public ResponseEntity<SnapshotResponse> create(@Valid @RequestBody CreateSnapshotRequest request) {
        RiskSnapshot s = snapshots.create(
                request.snapshotId(), request.regionCode(), request.version(),
                request.rainfall3hMm(), request.waterLevelM(),
                request.hazardPointStatus(), request.primaryRoadStatus(), request.secondaryRoadStatus(),
                request.vulnerablePopulation(), request.upstreamHealth(), request.observedAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(SnapshotResponse.from(s));
    }

    @GetMapping("/{snapshotId}")
    @Operation(summary = "快照详情")
    public SnapshotResponse get(@PathVariable String snapshotId) {
        return SnapshotResponse.from(snapshots.get(snapshotId));
    }

    @GetMapping
    @Operation(summary = "快照检索", description = "按 regionCode 过滤，分页返回")
    public Page<SnapshotResponse> search(@RequestParam(required = false) String regionCode, Pageable pageable) {
        return snapshots.search(regionCode, pageable).map(SnapshotResponse::from);
    }

    @PostMapping("/{snapshotId}/recompute")
    @Operation(summary = "重算", description = "对同一快照并发安全地追加一条新的计算建议（行锁 + seq 唯一约束）")
    public DecisionResponse recompute(@PathVariable String snapshotId) {
        return DecisionResponse.from(snapshots.recompute(snapshotId));
    }

    @GetMapping("/{snapshotId}/decisions")
    @Operation(summary = "建议历史", description = "含计算建议与覆写建议，按 seq 倒序，永不删除")
    public java.util.List<DecisionResponse> history(@PathVariable String snapshotId) {
        return decisions.history(snapshotId).stream().map(DecisionResponse::from).toList();
    }

    @GetMapping("/{snapshotId}/current-decision")
    @Operation(summary = "当前生效建议", description = "未过期覆写优先；覆写到期后自动恢复最新计算建议")
    public DecisionResponse current(@PathVariable String snapshotId) {
        return DecisionResponse.from(decisions.current(snapshotId));
    }
}
