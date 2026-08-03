package gov.basin.evac.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gov.basin.evac.api.dto.CreateSnapshotRequest;
import gov.basin.evac.api.dto.SnapshotResponse;
import gov.basin.evac.application.SnapshotService;
import gov.basin.evac.application.SnapshotService.UpstreamHealthInput;
import gov.basin.evac.domain.entity.RiskSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Immutable risk-snapshot intake and lookup. The controller only maps transport <-> service; it
 * contains no decision logic. Ingesting a snapshot triggers the initial recompute in the service.
 */
@RestController
@RequestMapping("/api/snapshots")
@Tag(name = "Snapshots", description = "不可变风险快照")
public class SnapshotController {

    private final SnapshotService snapshotService;

    public SnapshotController(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @PostMapping
    @Operation(summary = "Ingest a new immutable risk snapshot and compute its advisory")
    public ResponseEntity<SnapshotResponse> ingest(@Valid @RequestBody CreateSnapshotRequest request) {
        List<UpstreamHealthInput> health = request.upstreamHealth().stream()
                .map(h -> new UpstreamHealthInput(h.source(), h.status(), h.reportedVersion(), h.observedAt()))
                .toList();
        RiskSnapshot saved = snapshotService.ingest(
                request.snapshotId(), request.regionCode(), request.observedAt(),
                request.rainfall3hMm(), request.riverLevelM(), request.hazardPointStatus(),
                request.primaryRoadStatus(), request.secondaryRoadStatus(),
                request.vulnerablePopulationPersons(), health);
        return ResponseEntity.status(HttpStatus.CREATED).body(SnapshotResponse.from(saved));
    }

    @GetMapping("/{snapshotId}")
    @Operation(summary = "Get a risk snapshot with its per-feed upstream health")
    public SnapshotResponse get(@PathVariable String snapshotId) {
        return SnapshotResponse.from(snapshotService.get(snapshotId));
    }
}
