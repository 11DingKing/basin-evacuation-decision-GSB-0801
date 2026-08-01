package gov.basin.evac.api;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gov.basin.evac.api.dto.AdvisoryResponse;
import gov.basin.evac.api.dto.CreateOverrideRequest;
import gov.basin.evac.api.dto.OverrideResponse;
import gov.basin.evac.application.RecomputeService;
import gov.basin.evac.application.OverrideService;
import gov.basin.evac.domain.entity.ManualOverride;
import gov.basin.evac.domain.model.DecisionLevel;
import gov.basin.evac.domain.repository.AdvisoryRepository;
import gov.basin.evac.domain.repository.ManualOverrideRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Advisory detail/search plus recompute and manual-override endpoints. The controller delegates
 * every decision to the domain services; it never encodes threshold, override or notification
 * rules itself.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Advisories", description = "四级决策建议：详情、检索、覆写")
public class AdvisoryController {

    private final AdvisoryRepository advisoryRepository;
    private final ManualOverrideRepository overrideRepository;
    private final RecomputeService recomputeService;
    private final OverrideService overrideService;

    public AdvisoryController(AdvisoryRepository advisoryRepository,
                              ManualOverrideRepository overrideRepository,
                              RecomputeService recomputeService,
                              OverrideService overrideService) {
        this.advisoryRepository = advisoryRepository;
        this.overrideRepository = overrideRepository;
        this.recomputeService = recomputeService;
        this.overrideService = overrideService;
    }

    @GetMapping("/advisories/{id}")
    @Operation(summary = "Get one advisory by id (cites its evidence version)")
    public ResponseEntity<AdvisoryResponse> getById(@PathVariable Long id) {
        return advisoryRepository.findById(id)
                .map(AdvisoryResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/advisories")
    @Operation(summary = "Search advisories by region and/or level; currentOnly hides history")
    public Page<AdvisoryResponse> search(
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) DecisionLevel level,
            @RequestParam(defaultValue = "true") boolean currentOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return advisoryRepository.search(regionCode, level, currentOnly, PageRequest.of(page, size))
                .map(AdvisoryResponse::from);
    }

    @GetMapping("/snapshots/{snapshotId}/advisory")
    @Operation(summary = "Get the current advisory for a snapshot")
    public ResponseEntity<AdvisoryResponse> currentForSnapshot(@PathVariable String snapshotId) {
        return advisoryRepository
                .findFirstBySnapshotIdAndSupersededFalseOrderByComputedAtDesc(snapshotId)
                .map(AdvisoryResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/snapshots/{snapshotId}/advisories")
    @Operation(summary = "Full advisory history for a snapshot (append-only, never deleted)")
    public List<AdvisoryResponse> historyForSnapshot(@PathVariable String snapshotId) {
        return advisoryRepository.findBySnapshotIdOrderByComputedAtDesc(snapshotId)
                .stream().map(AdvisoryResponse::from).toList();
    }

    @PostMapping("/snapshots/{snapshotId}/recompute")
    @Operation(summary = "Recompute the advisory for a snapshot (idempotent, override-aware)")
    public AdvisoryResponse recompute(@PathVariable String snapshotId) {
        return AdvisoryResponse.from(recomputeService.recompute(snapshotId));
    }

    @PostMapping("/snapshots/{snapshotId}/overrides")
    @Operation(summary = "Create an immutable manual override (operator, reason, expiry required)")
    public ResponseEntity<OverrideResponse> createOverride(
            @PathVariable String snapshotId,
            @Valid @RequestBody CreateOverrideRequest request) {
        ManualOverride override = overrideService.createOverride(
                snapshotId, request.forcedLevel(), request.operator(), request.reason(),
                request.effectiveFrom(), request.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(OverrideResponse.from(override));
    }

    @GetMapping("/snapshots/{snapshotId}/overrides")
    @Operation(summary = "List the override history for a snapshot (never deleted)")
    public List<OverrideResponse> overrideHistory(@PathVariable String snapshotId) {
        return overrideRepository.findBySnapshotIdOrderByEffectiveFromDesc(snapshotId)
                .stream().map(OverrideResponse::from).toList();
    }
}
