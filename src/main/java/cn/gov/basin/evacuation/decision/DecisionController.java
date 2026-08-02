package cn.gov.basin.evacuation.decision;

import cn.gov.basin.evacuation.domain.decision.DecisionLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@Tag(name = "Decision Advices", description = "四级决策建议与证据引用")
@RestController
@RequestMapping("/api/advices")
public class DecisionController {

    private final DecisionService service;

    public DecisionController(DecisionService service) {
        this.service = service;
    }

    @Operation(summary = "基于指定快照重算决策（幂等/并发安全）")
    @PostMapping("/compute/{snapshotId}")
    public AdviceDto compute(@PathVariable String snapshotId) {
        return AdviceDto.from(service.computeForSnapshot(snapshotId));
    }

    @Operation(summary = "获取决策详情")
    @GetMapping("/{id}")
    public AdviceDto get(@PathVariable Long id) {
        return AdviceDto.from(service.get(id));
    }

    @Operation(summary = "获取某快照的最新决策")
    @GetMapping("/by-snapshot/{snapshotId}/latest")
    public AdviceDto latestForSnapshot(@PathVariable String snapshotId) {
        return service.latestForSnapshot(snapshotId)
                .map(AdviceDto::from)
                .orElseThrow(() -> new AdviceNotFoundException(null));
    }

    @Operation(summary = "按行政区 / 等级检索决策")
    @GetMapping
    public Page<AdviceDto> search(@RequestParam(required = false) String regionCode,
                                  @RequestParam(required = false) DecisionLevel level,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return service.search(regionCode, level, page, size).map(AdviceDto::from);
    }

    public record AdviceDto(
            Long id,
            String snapshotId,
            String regionCode,
            DecisionLevel decisionLevel,
            String source,
            String adviceText,
            Map<String, Object> evidenceRef,
            Long overrideId,
            Instant computedAt,
            Instant createdAt
    ) {
        static AdviceDto from(DecisionAdvice a) {
            return new AdviceDto(
                    a.getId(),
                    a.getSnapshotId(),
                    a.getRegionCode(),
                    a.getDecisionLevel(),
                    a.getSource().name(),
                    a.getAdviceText(),
                    a.getEvidenceRef(),
                    a.getOverrideId(),
                    a.getComputedAt(),
                    a.getCreatedAt()
            );
        }
    }
}
