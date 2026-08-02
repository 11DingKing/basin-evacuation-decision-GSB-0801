package cn.gov.basin.evacuation.snapshot;

import cn.gov.basin.evacuation.domain.snapshot.HazardStatus;
import cn.gov.basin.evacuation.domain.snapshot.RoadStatus;
import cn.gov.basin.evacuation.domain.snapshot.UpstreamHealth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Tag(name = "Risk Snapshots", description = "不可变风险快照")
@RestController
@RequestMapping("/api/snapshots")
public class SnapshotController {

    private final SnapshotService service;

    public SnapshotController(SnapshotService service) {
        this.service = service;
    }

    @Operation(summary = "摄取一条新的风险快照（不可变）")
    @PostMapping
    public SnapshotDto create(@Valid @RequestBody CreateSnapshotCommand cmd) {
        return SnapshotDto.from(service.ingest(cmd));
    }

    @Operation(summary = "按 snapshotId 获取快照详情")
    @GetMapping("/{snapshotId}")
    public SnapshotDto get(@PathVariable String snapshotId) {
        return SnapshotDto.from(service.get(snapshotId));
    }

    @Operation(summary = "按行政区检索快照（分页）")
    @GetMapping
    public Page<SnapshotDto> search(@RequestParam(required = false) String regionCode,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        Page<RiskSnapshot> result = service.search(regionCode, page, size);
        return result.map(SnapshotDto::from);
    }

    public record SnapshotDto(
            String snapshotId,
            String regionCode,
            Instant observedAt,
            BigDecimal rainfall3hMm,
            BigDecimal waterLevelM,
            HazardStatus hazardStatus,
            RoadStatus primaryRoadStatus,
            RoadStatus secondaryRoadStatus,
            Integer vulnerablePopulation,
            String populationUnit,
            UpstreamHealth rainfallHealth,
            UpstreamHealth waterLevelHealth,
            UpstreamHealth hazardHealth,
            UpstreamHealth infrastructureHealth,
            String rainfallVersion,
            String waterLevelVersion,
            String hazardVersion,
            String infrastructureVersion,
            Instant createdAt
    ) {
        public static SnapshotDto from(RiskSnapshot s) {
            return new SnapshotDto(
                    s.getSnapshotId(),
                    s.getRegionCode(),
                    s.getObservedAt(),
                    s.getRainfall3hMm(),
                    s.getWaterLevelM(),
                    s.getHazardStatus(),
                    s.getPrimaryRoadStatus(),
                    s.getSecondaryRoadStatus(),
                    s.getVulnerablePopulation(),
                    "PERSON",
                    s.getRainfallHealth(),
                    s.getWaterLevelHealth(),
                    s.getHazardHealth(),
                    s.getInfrastructureHealth(),
                    s.getRainfallVersion(),
                    s.getWaterLevelVersion(),
                    s.getHazardVersion(),
                    s.getInfrastructureVersion(),
                    s.getCreatedAt()
            );
        }
    }
}
