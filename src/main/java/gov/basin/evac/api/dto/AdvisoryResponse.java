package gov.basin.evac.api.dto;

import java.time.Instant;

import gov.basin.evac.domain.entity.Advisory;

/**
 * API view of an advisory. Every response makes the cited evidence explicit ({@code
 * evidenceVersion} + {@code snapshotId}) so operators can see exactly which evidence version
 * produced the recommendation.
 */
public record AdvisoryResponse(
        Long id,
        String snapshotId,
        String regionCode,
        long evidenceVersion,
        String decisionLevel,
        int priority,
        String source,
        Long overrideId,
        String rationale,
        int affectedPersons,
        String populationUnit,
        Instant computedAt,
        boolean superseded) {

    public static AdvisoryResponse from(Advisory a) {
        return new AdvisoryResponse(
                a.getId(), a.getSnapshotId(), a.getRegionCode(), a.getEvidenceVersion(),
                a.getDecisionLevel().name(), a.getPriority(), a.getSource().name(),
                a.getOverrideId(), a.getRationale(), a.getAffectedPersons(),
                "persons", a.getComputedAt(), a.isSuperseded());
    }
}
