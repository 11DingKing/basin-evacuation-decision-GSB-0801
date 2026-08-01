package com.example.basin.evacuation.api.dto;

import com.example.basin.evacuation.domain.override.ManualOverride;
import com.example.basin.evacuation.domain.shared.DecisionLevel;

import java.time.Instant;

public record OverrideResponse(
        Long id,
        String snapshotId,
        String operator,
        String reason,
        DecisionLevel targetLevel,
        Instant expiresAt,
        Instant createdAt
) {
    public static OverrideResponse from(ManualOverride o) {
        return new OverrideResponse(
                o.getId(),
                o.getSnapshotId(),
                o.getOperator(),
                o.getReason(),
                o.getTargetLevel(),
                o.getExpiresAt(),
                o.getCreatedAt());
    }
}
