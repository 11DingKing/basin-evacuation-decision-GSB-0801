package com.example.basin.evacuation.api.controller;

import com.example.basin.evacuation.api.dto.DecisionResponse;
import com.example.basin.evacuation.api.dto.SnapshotRequest;
import com.example.basin.evacuation.api.dto.SnapshotResponse;
import com.example.basin.evacuation.application.DecisionService;
import com.example.basin.evacuation.application.SnapshotService;
import com.example.basin.evacuation.domain.snapshot.RiskSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/snapshots")
@Tag(name = "Snapshots", description = "Immutable upstream risk evidence snapshots")
public class SnapshotController {

    private final SnapshotService snapshotService;
    private final DecisionService decisionService;

    public SnapshotController(SnapshotService snapshotService, DecisionService decisionService) {
        this.snapshotService = snapshotService;
        this.decisionService = decisionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ingest an immutable risk snapshot (one frozen evidence bundle)")
    public SnapshotResponse ingest(@Valid @RequestBody SnapshotRequest request) {
        RiskSnapshot snapshot = RiskSnapshot.builder()
                .snapshotId(request.snapshotId())
                .districtCode(request.districtCode())
                .observedAt(request.observedAt())
                .rainfall3hMm(request.rainfall3hMm())
                .waterLevelM(request.waterLevelM())
                .hazardStatus(request.hazardStatus())
                .mainRoadStatus(request.mainRoadStatus())
                .secondaryRoadStatus(request.secondaryRoadStatus())
                .vulnerablePopulation(request.vulnerablePopulation())
                .rainfallHealth(request.rainfallHealth())
                .waterLevelHealth(request.waterLevelHealth())
                .hazardHealth(request.hazardHealth())
                .roadHealth(request.roadHealth())
                .rainfallVersion(request.rainfallVersion())
                .waterLevelVersion(request.waterLevelVersion())
                .hazardVersion(request.hazardVersion())
                .roadVersion(request.roadVersion())
                .evidenceVersion(request.evidenceVersion())
                .build();
        return SnapshotResponse.from(snapshotService.ingest(snapshot));
    }

    @GetMapping("/{snapshotId}")
    @Operation(summary = "Get a snapshot by id")
    public SnapshotResponse get(@PathVariable String snapshotId) {
        return SnapshotResponse.from(snapshotService.get(snapshotId));
    }

    @GetMapping(params = "districtCode")
    @Operation(summary = "List snapshots for a district, newest observation first")
    public List<SnapshotResponse> byDistrict(@org.springframework.web.bind.annotation.RequestParam String districtCode) {
        return snapshotService.listByDistrict(districtCode).stream()
                .map(SnapshotResponse::from).toList();
    }

    @PostMapping("/{snapshotId}/recompute")
    @Operation(summary = "Recompute the decision for a snapshot; appends an immutable decision row and outbox entries")
    public DecisionResponse recompute(@PathVariable String snapshotId) {
        return DecisionResponse.from(decisionService.recompute(snapshotId));
    }
}
