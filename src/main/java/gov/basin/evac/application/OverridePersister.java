package gov.basin.evac.application;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import gov.basin.evac.domain.entity.ManualOverride;
import gov.basin.evac.domain.entity.RiskSnapshot;
import gov.basin.evac.domain.model.DecisionLevel;
import gov.basin.evac.domain.repository.ManualOverrideRepository;
import gov.basin.evac.domain.repository.RiskSnapshotRepository;

/**
 * Persists a single manual override in its OWN transaction. Kept separate from
 * {@link OverrideService} so the {@code REQUIRES_NEW} boundary is honoured by Spring's proxy:
 * if a concurrent request with the same {@code requestId} wins the race, the unique index throws
 * and only THIS transaction is rolled back, leaving the caller free to re-fetch the winner.
 */
@Component
public class OverridePersister {

    private final ManualOverrideRepository overrideRepository;
    private final RiskSnapshotRepository snapshotRepository;

    public OverridePersister(ManualOverrideRepository overrideRepository,
                             RiskSnapshotRepository snapshotRepository) {
        this.overrideRepository = overrideRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ManualOverride insert(String snapshotId, DecisionLevel forcedLevel, String operator,
                                 String reason, Instant effectiveFrom, Instant expiresAt,
                                 String requestId) {
        RiskSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new SnapshotNotFoundException(snapshotId));
        // saveAndFlush forces the INSERT (and thus the unique-index check) to run now, inside this
        // transaction, so a duplicate requestId surfaces as an exception here rather than later.
        return overrideRepository.saveAndFlush(new ManualOverride(
                snapshotId, snapshot.getRegionCode(), forcedLevel, operator, reason,
                effectiveFrom, expiresAt, requestId));
    }
}
