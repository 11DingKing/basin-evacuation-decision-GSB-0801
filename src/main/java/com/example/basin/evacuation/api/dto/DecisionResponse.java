package com.example.basin.evacuation.api.dto;

import com.example.basin.evacuation.domain.decision.Decision;
import com.example.basin.evacuation.domain.decision.DimensionBreakdown;
import com.example.basin.evacuation.domain.shared.DecisionLevel;

import java.time.Instant;

public record DecisionResponse(
        Long id,
        String snapshotId,
        String districtCode,
        DecisionLevel level,
        DecisionLevel computedLevel,
        Long activeOverrideId,
        String requestNo,
        String evidenceVersion,
        String rationale,
        DimensionBreakdown dimensionBreakdown,
        int sequenceNo,
        Instant createdAt
) {
    public static DecisionResponse from(Decision d) {
        return new DecisionResponse(
                d.getId(),
                d.getSnapshotId(),
                d.getDistrictCode(),
                d.getLevel(),
                d.getComputedLevel(),
                d.getActiveOverrideId(),
                d.getRequestNo(),
                d.getEvidenceVersion(),
                d.getRationale(),
                d.getDimensionBreakdown(),
                d.getSequenceNo(),
                d.getCreatedAt());
    }
}
