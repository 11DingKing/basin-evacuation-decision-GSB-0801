package com.basin.evacuation.override_;

import com.basin.evacuation.decision.DecisionOutcome;
import com.basin.evacuation.decision.DecisionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/snapshots/{snapshotId}/overrides")
@Tag(name = "人工覆写", description = "覆写必须包含操作者、理由、过期时间；到期自动恢复计算结果；覆写永不删除")
public class OverrideController {

    private final OverrideService service;

    public OverrideController(OverrideService service) {
        this.service = service;
    }

    public record CreateOverrideRequest(
            @NotBlank String requestId,
            @NotNull DecisionOutcome outcome,
            @NotBlank String operator,
            @NotBlank String reason,
            @NotNull Instant expiresAt) {}

    public record OverrideResponse(
            UUID id, String snapshotId, String requestId, DecisionOutcome outcome,
            String operator, String reason, Instant createdAt, Instant expiresAt) {
        static OverrideResponse from(ManualOverride o) {
            return new OverrideResponse(o.getId(), o.getSnapshotId(), o.getRequestId(), o.getOutcome(),
                    o.getOperator(), o.getReason(), o.getCreatedAt(), o.getExpiresAt());
        }
    }

    @PostMapping
    @Operation(summary = "创建覆写", description = "requestId 为幂等依据：相同请求号且内容一致返回已有建议（不新增覆写/建议/outbox），内容不一致返回 409；同事务追加 source=OVERRIDE 建议并写 outbox")
    public ResponseEntity<DecisionResponse> create(@PathVariable String snapshotId,
                                                   @Valid @RequestBody CreateOverrideRequest request) {
        var decision = service.recordOverride(snapshotId, request.requestId(), request.outcome(),
                request.operator(), request.reason(), request.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(DecisionResponse.from(decision));
    }

    @GetMapping
    @Operation(summary = "覆写列表", description = "含已过期覆写（保留审计）")
    public List<OverrideResponse> list(@PathVariable String snapshotId) {
        return service.list(snapshotId).stream().map(OverrideResponse::from).toList();
    }
}
