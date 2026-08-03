package cn.gov.basin.evacuation.domain.threshold;

import cn.gov.basin.evacuation.domain.decision.DecisionLevel;

import java.util.List;

public record EvaluationResult(
        DecisionLevel level,
        String message,
        List<String> triggeredRules,
        List<String> missingEvidence,
        EvidenceRef evidenceRef
) {
    public boolean isInsufficient() {
        return level == DecisionLevel.INSUFFICIENT_DATA;
    }
}
