package gov.basin.evac.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import gov.basin.evac.domain.entity.ManualOverride;
import gov.basin.evac.domain.model.DecisionLevel;
import gov.basin.evac.domain.repository.ManualOverrideRepository;

/**
 * Creates manual overrides. An override always records operator, reason and an expiry, and its
 * window must be valid. Creating an override immediately triggers a recompute so the current
 * advisory reflects it at once. The override itself is immutable and never deleted.
 *
 * <p>When a business request number ({@code requestId}) is supplied, creation is idempotent:
 * replaying the same request returns the existing override and never produces a second override,
 * advisory, or outbox entry. Idempotency is enforced at two layers — an up-front lookup for the
 * common case, and a unique index that makes concurrent replay safe (the loser re-reads the
 * winner). The subsequent recompute is itself idempotent, so it emits at most one advisory/outbox.
 */
@Service
public class OverrideService {

    private final ManualOverrideRepository overrideRepository;
    private final OverridePersister overridePersister;
    private final RecomputeService recomputeService;
    private final Clock clock;

    public OverrideService(ManualOverrideRepository overrideRepository,
                           OverridePersister overridePersister,
                           RecomputeService recomputeService,
                           Clock clock) {
        this.overrideRepository = overrideRepository;
        this.overridePersister = overridePersister;
        this.recomputeService = recomputeService;
        this.clock = clock;
    }

    public ManualOverride createOverride(String snapshotId, DecisionLevel forcedLevel,
                                         String operator, String reason,
                                         Instant effectiveFrom, Instant expiresAt) {
        return createOverride(snapshotId, forcedLevel, operator, reason, effectiveFrom, expiresAt, null);
    }

    public ManualOverride createOverride(String snapshotId, DecisionLevel forcedLevel,
                                         String operator, String reason,
                                         Instant effectiveFrom, Instant expiresAt,
                                         String requestId) {
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

        String normalizedRequestId = (requestId != null && requestId.isBlank()) ? null : requestId;

        ManualOverride override;
        if (normalizedRequestId == null) {
            override = overridePersister.insert(snapshotId, forcedLevel, operator, reason,
                    from, expiresAt, null);
        } else {
            // Fast path: request already processed -> return it, create nothing new.
            Optional<ManualOverride> existing = overrideRepository.findByRequestId(normalizedRequestId);
            if (existing.isPresent()) {
                override = existing.get();
            } else {
                try {
                    override = overridePersister.insert(snapshotId, forcedLevel, operator, reason,
                            from, expiresAt, normalizedRequestId);
                } catch (DataIntegrityViolationException raceLost) {
                    // A concurrent replay won the unique-index race; re-read its override.
                    override = overrideRepository.findByRequestId(normalizedRequestId)
                            .orElseThrow(() -> raceLost);
                }
            }
        }

        // Reflect the override in the current advisory. Recompute is idempotent under the snapshot
        // lock, so replays collapse onto the same single advisory + outbox record.
        recomputeService.recompute(snapshotId);
        return override;
    }
}
