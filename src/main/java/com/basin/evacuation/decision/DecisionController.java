package com.basin.evacuation.decision;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/decisions")
@Tag(name = "决策建议", description = "四级决策建议的详情与检索；每条建议标注证据版本（snapshotId + snapshotVersion）")
public class DecisionController {

    private final DecisionService service;

    public DecisionController(DecisionService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @Operation(summary = "建议详情")
    public DecisionResponse get(@PathVariable UUID id) {
        return DecisionResponse.from(service.get(id));
    }

    @GetMapping
    @Operation(summary = "建议检索", description = "按地区/快照/结果/时间窗过滤；outcome 可取 EVACUATE_NOW、PRE_TRANSFER、PREPARE、LOW_RISK、INSUFFICIENT_DATA")
    public Page<DecisionResponse> search(
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String snapshotId,
            @RequestParam(required = false) DecisionOutcome outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            Pageable pageable) {
        return service.search(regionCode, snapshotId, outcome, from, to, pageable)
                .map(DecisionResponse::from);
    }
}
