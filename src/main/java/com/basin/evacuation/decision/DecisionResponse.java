package com.basin.evacuation.decision;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DecisionResponse(
        UUID id,
        String snapshotId,
        int snapshotVersion,
        String regionCode,
        int seq,
        DecisionOutcome outcome,
        String outcomeLabel,
        int priority,
        DecisionSource source,
        UUID overrideId,
        String requestId,
        List<String> reasons,
        DecisionEvidence evidence,
        Instant createdAt) {

    public static DecisionResponse from(Decision d) {
        return new DecisionResponse(
                d.getId(), d.getSnapshotId(), d.getSnapshotVersion(), d.getRegionCode(), d.getSeq(),
                d.getOutcome(), d.getOutcome().label(), d.getOutcome().priority(),
                d.getSource(), d.getOverrideId(), d.getRequestId(), d.getReasons(), d.getEvidence(), d.getCreatedAt());
    }
}
