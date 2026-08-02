package com.example.basin.evacuation.api.controller;

import com.example.basin.evacuation.api.dto.DecisionResponse;
import com.example.basin.evacuation.api.dto.OverrideRequest;
import com.example.basin.evacuation.api.dto.OverrideResponse;
import com.example.basin.evacuation.application.DecisionService;
import com.example.basin.evacuation.domain.override.ManualOverride;
import com.example.basin.evacuation.domain.override.OverrideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/overrides")
@Tag(name = "Overrides", description = "Append-only manual overrides with operator/reason/expiry and idempotent request number")
public class OverrideController {

    private final OverrideService overrideService;
    private final DecisionService decisionService;

    public OverrideController(OverrideService overrideService, DecisionService decisionService) {
        this.overrideService = overrideService;
        this.decisionService = decisionService;
    }

    @PostMapping("/snapshots/{snapshotId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an idempotent manual override for a snapshot; history is never deleted")
    public OverrideResponse create(@PathVariable String snapshotId,
                                   @Valid @RequestBody OverrideRequest request) {
        ManualOverride saved = overrideService.create(
                snapshotId,
                request.requestNo(),
                request.operator(),
                request.reason(),
                request.targetLevel(),
                request.expiresAt());
        return OverrideResponse.from(saved);
    }

    @PostMapping("/snapshots/{snapshotId}/apply")
    @Operation(summary = "Create an idempotent override and recompute; replaying the same requestNo returns the same decision")
    public DecisionResponse createAndRecompute(@PathVariable String snapshotId,
                                               @Valid @RequestBody OverrideRequest request) {
        return DecisionResponse.from(decisionService.applyOverride(
                snapshotId,
                request.requestNo(),
                request.operator(),
                request.reason(),
                request.targetLevel(),
                request.expiresAt()));
    }

    @GetMapping(params = "snapshotId")
    @Operation(summary = "List the full override history for a snapshot, newest first")
    public List<OverrideResponse> history(@RequestParam String snapshotId) {
        return overrideService.history(snapshotId).stream()
                .map(OverrideResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an override by id")
    public OverrideResponse get(@PathVariable Long id) {
        return overrideService.findById(id)
                .map(OverrideResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("override not found: " + id));
    }
}
