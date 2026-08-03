package com.example.basin.evacuation.api.controller;

import com.example.basin.evacuation.api.dto.DecisionResponse;
import com.example.basin.evacuation.application.DecisionService;
import com.example.basin.evacuation.domain.decision.Decision;
import com.example.basin.evacuation.domain.notification.OutboxRepository;
import com.example.basin.evacuation.domain.notification.NotificationOutbox;
import com.example.basin.evacuation.domain.shared.DecisionLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/decisions")
@Tag(name = "Decisions", description = "Append-only evacuation decisions with evidence-version traceability")
public class DecisionController {

    private final DecisionService decisionService;
    private final OutboxRepository outboxRepository;

    public DecisionController(DecisionService decisionService, OutboxRepository outboxRepository) {
        this.decisionService = decisionService;
        this.outboxRepository = outboxRepository;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a decision by id (detail)")
    public DecisionResponse get(@PathVariable Long id) {
        return decisionService.getById(id)
                .map(DecisionResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("decision not found: " + id));
    }

    @GetMapping(params = "snapshotId")
    @Operation(summary = "List the full decision history for a snapshot, newest sequence first")
    public List<DecisionResponse> history(@RequestParam String snapshotId) {
        return decisionService.getHistory(snapshotId).stream()
                .map(DecisionResponse::from).toList();
    }

    @GetMapping(value = "/latest", params = "snapshotId")
    @Operation(summary = "Get the latest decision for a snapshot")
    public DecisionResponse latest(@RequestParam String snapshotId) {
        return decisionService.getLatest(snapshotId)
                .map(DecisionResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("no decision for snapshot: " + snapshotId));
    }

    @GetMapping("/search")
    @Operation(summary = "Search decisions by district and/or level, newest first")
    public PagedModel<DecisionResponse> search(@RequestParam(required = false) String districtCode,
                                               @RequestParam(required = false) DecisionLevel level,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        Page<Decision> result = decisionService.search(districtCode, level, pageable);
        Page<DecisionResponse> mapped = result.map(DecisionResponse::from);
        return new PagedModel<>(mapped);
    }

    @GetMapping("/{id}/notifications")
    @Operation(summary = "Inspect notification outbox rows produced by a decision (for replay/observability)")
    public List<NotificationOutbox> notifications(@PathVariable Long id) {
        return outboxRepository.findByDecisionIdOrderByIdAsc(id);
    }
}
