package gov.basin.evac.api.dto;

import java.time.Instant;

import gov.basin.evac.domain.model.DecisionLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request to create a manual override. Operator, reason and expiry are mandatory; effectiveFrom
 * defaults to "now" (per the injectable clock) when omitted. An optional {@code requestId}
 * (business request number) makes creation idempotent — replaying the same requestId returns the
 * existing override without creating duplicates.
 */
public record CreateOverrideRequest(
        @NotNull DecisionLevel forcedLevel,
        @NotBlank String operator,
        @NotBlank String reason,
        Instant effectiveFrom,
        @NotNull Instant expiresAt,
        String requestId) {
}
