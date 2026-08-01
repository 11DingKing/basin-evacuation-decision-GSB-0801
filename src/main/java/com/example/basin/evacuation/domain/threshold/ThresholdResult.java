package com.example.basin.evacuation.domain.threshold;

import com.example.basin.evacuation.domain.decision.DimensionBreakdown;
import com.example.basin.evacuation.domain.shared.DecisionLevel;

public record ThresholdResult(
        DecisionLevel computedLevel,
        DimensionBreakdown breakdown,
        String rationale
) {
}
