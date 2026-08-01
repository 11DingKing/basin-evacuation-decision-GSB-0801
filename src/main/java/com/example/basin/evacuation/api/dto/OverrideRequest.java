package com.example.basin.evacuation.api.dto;

import com.example.basin.evacuation.domain.shared.DecisionLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record OverrideRequest(
        @NotBlank String operator,
        @NotBlank String reason,
        @NotNull DecisionLevel targetLevel,
        @NotNull Instant expiresAt
) {
}
