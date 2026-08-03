package cn.gov.basin.evacuation.override;

import cn.gov.basin.evacuation.decision.DecisionController;
import cn.gov.basin.evacuation.domain.decision.DecisionLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Tag(name = "Manual Overrides", description = "人工覆写（含操作者/理由/过期时间，不可删除；requestId 幂等）")
@RestController
@RequestMapping("/api/overrides")
public class OverrideController {

    private final OverrideService service;
    private final OverrideApplicationService applicationService;

    public OverrideController(OverrideService service,
                              OverrideApplicationService applicationService) {
        this.service = service;
        this.applicationService = applicationService;
    }

    @Operation(summary = "创建人工覆写（可选 requestId 做幂等；不触发决策计算）")
    @PostMapping
    public OverrideDto create(@Valid @RequestBody CreateOverrideCommand cmd) {
        return OverrideDto.from(service.create(cmd));
    }

    @Operation(summary = "幂等地应用覆写并基于其绑定的快照计算决策（requestId 重复时返回已存在结果，不重复生成 outbox）")
    @PostMapping("/apply")
    public OverrideApplicationDto apply(@Valid @RequestBody CreateOverrideCommand cmd) {
        OverrideApplicationResult result = applicationService.apply(cmd);
        return new OverrideApplicationDto(
                OverrideDto.from(result.override()),
                DecisionController.AdviceDto.from(result.advice()),
                result.created()
        );
    }

    @Operation(summary = "获取覆写详情")
    @GetMapping("/{id}")
    public OverrideDto get(@PathVariable Long id) {
        return OverrideDto.from(service.get(id));
    }

    @Operation(summary = "按行政区列出覆写历史（不区分是否过期，按时间倒序）")
    @GetMapping
    public List<OverrideDto> history(@org.springframework.web.bind.annotation.RequestParam String regionCode) {
        return service.history(regionCode).stream().map(OverrideDto::from).toList();
    }

    public record OverrideDto(
            Long id,
            String regionCode,
            String operator,
            String reason,
            DecisionLevel targetLevel,
            String status,
            Instant effectiveFrom,
            Instant expiresAt,
            Instant createdAt,
            String requestId,
            String snapshotId
    ) {
        static OverrideDto from(ManualOverride o) {
            return new OverrideDto(
                    o.getId(),
                    o.getRegionCode(),
                    o.getOperator(),
                    o.getReason(),
                    o.getTargetLevel(),
                    o.getStatus().name(),
                    o.getEffectiveFrom(),
                    o.getExpiresAt(),
                    o.getCreatedAt(),
                    o.getRequestId(),
                    o.getSnapshotId()
            );
        }
    }

    public record OverrideApplicationDto(
            OverrideDto override,
            DecisionController.AdviceDto advice,
            boolean created
    ) {
    }
}
