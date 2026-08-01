package com.example.basin.evacuation.domain.decision;

import com.example.basin.evacuation.domain.shared.DecisionLevel;
import com.example.basin.evacuation.domain.shared.UpstreamType;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record DimensionBreakdown(
        boolean dataSufficient,
        List<UpstreamType> missingDimensions,
        List<UpstreamType> staleDimensions,
        Map<Dimension, DimensionAssessment> dimensions
) {
    @Builder
    public record DimensionAssessment(
            DecisionLevel severity,
            boolean healthy,
            boolean usable,
            String value,
            String detail
    ) {
    }
}
