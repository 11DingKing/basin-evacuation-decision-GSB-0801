package gov.basin.evac.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gov.basin.evac.domain.entity.ManualOverride;
import gov.basin.evac.domain.entity.RiskSnapshot;
import gov.basin.evac.domain.model.DecisionLevel;
import gov.basin.evac.domain.repository.ManualOverrideRepository;
import gov.basin.evac.domain.repository.RiskSnapshotRepository;

/**
 * Creates manual overrides. An override always records operator, reason and an expiry, and its
 * window must be valid. Creating an override immediately triggers a recompute so the current
 * advisory reflects it at once. The override itself is immutable and never deleted.
 */
@Service
public class OverrideService {

    private final ManualOverrideRepository overrideRepository;
    private final RiskSnapshotRepository snapshotRepository;
    private final RecomputeService recomputeService;
    private final Clock clock;

    public OverrideService(ManualOverrideRepository overrideRepository,
                           RiskSnapshotRepository snapshotRepository,
                           RecomputeService recomputeService,
                           Clock clock) {
        this.overrideRepository = overrideRepository;
        this.snapshotRepository = snapshotRepository;
        this.recomputeService = recomputeService;
        this.clock = clock;
    }

    @Transactional
    public ManualOverride createOverride(String snapshotId, DecisionLevel forcedLevel,
                                         String operator, String reason,
                                         Instant effectiveFrom, Instant expiresAt) {
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("override operator is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("override reason is required");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("override expiry is required");
        }
        Instant from = effectiveFrom != null ? effectiveFrom : clock.instant();
        if (!expiresAt.isAfter(from)) {
            throw new IllegalArgumentException("override expiry must be after its effective time");
        }

        RiskSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new SnapshotNotFoundException(snapshotId));

        ManualOverride override = overrideRepository.save(new ManualOverride(
                snapshotId, snapshot.getRegionCode(), forcedLevel, operator, reason, from, expiresAt));

        // Reflect the new override in the current advisory right away.
        recomputeService.recompute(snapshotId);
        return override;
    }
}
