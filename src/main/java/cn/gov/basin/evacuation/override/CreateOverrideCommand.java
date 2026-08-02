package cn.gov.basin.evacuation.override;

import cn.gov.basin.evacuation.domain.decision.DecisionLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateOverrideCommand(
        @NotBlank String regionCode,
        @NotBlank String operator,
        @NotBlank String reason,
        @NotNull DecisionLevel targetLevel,
        @NotNull Instant effectiveFrom,
        @NotNull Instant expiresAt
) {
}
