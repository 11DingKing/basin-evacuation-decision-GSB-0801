package gov.basin.evac.api.dto;

import java.time.Instant;

import gov.basin.evac.domain.entity.ManualOverride;

/** API view of an immutable manual override. */
public record OverrideResponse(
        Long id,
        String snapshotId,
        String regionCode,
        String forcedLevel,
        String operator,
        String reason,
        Instant effectiveFrom,
        Instant expiresAt,
        Instant createdAt,
        String requestId) {

    public static OverrideResponse from(ManualOverride o) {
        return new OverrideResponse(o.getId(), o.getSnapshotId(), o.getRegionCode(),
                o.getForcedLevel().name(), o.getOperator(), o.getReason(),
                o.getEffectiveFrom(), o.getExpiresAt(), o.getCreatedAt(), o.getRequestId());
    }
}
