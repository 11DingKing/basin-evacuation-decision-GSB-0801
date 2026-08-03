package com.example.basin.evacuation.domain.notification;

import com.example.basin.evacuation.domain.shared.DecisionLevel;
import lombok.Builder;

import java.util.List;

@Builder
public record NotificationPayload(
        String snapshotId,
        String districtCode,
        long decisionId,
        DecisionLevel level,
        int sequenceNo,
        long vulnerablePopulation,
        String evidenceVersion,
        String rationale,
        List<String> recommendedActions,
        String requestNo,
        UpstreamVersions upstreamVersions
) {
    @Builder
    public record UpstreamVersions(
            String rainfall,
            String waterLevel,
            String hazard,
            String road
    ) {
    }
}
