package gov.basin.evac.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import gov.basin.evac.domain.entity.Advisory;
import gov.basin.evac.domain.entity.ManualOverride;
import gov.basin.evac.domain.entity.NotificationOutbox;
import gov.basin.evac.domain.entity.RiskSnapshot;
import gov.basin.evac.domain.model.AdvisorySource;
import gov.basin.evac.domain.model.DecisionLevel;
import gov.basin.evac.domain.notification.NotificationComposer;
import gov.basin.evac.domain.override.OverrideResolver;
import gov.basin.evac.domain.repository.AdvisoryRepository;
import gov.basin.evac.domain.repository.ManualOverrideRepository;
import gov.basin.evac.domain.repository.NotificationOutboxRepository;
import gov.basin.evac.domain.repository.RiskSnapshotRepository;
import gov.basin.evac.domain.threshold.DecisionEvaluation;
import gov.basin.evac.domain.threshold.ThresholdEvaluator;

/**
 * Orchestrates a full recompute of the advisory for one snapshot. This is the only place the
 * three independent domain modules (threshold / override / notification) are composed, and it
 * owns the transactional guarantees:
 *
 * <ul>
 *   <li><b>Serialized per snapshot</b> — a pessimistic write lock on the snapshot row means two
 *       concurrent recomputes of the same snapshot run one after another, so exactly one current
 *       advisory survives (no duplicate "current" rows).</li>
 *   <li><b>Idempotent</b> — if the already-current advisory matches the newly decided outcome
 *       (same level, same source, same evidence version, same override), nothing is written.</li>
 *   <li><b>All-or-nothing</b> — superseding old advisories, inserting the new advisory, and
 *       enqueuing its notification happen in one transaction. There is never an advisory without
 *       its outbox record, nor a "looks committed but half-written" state.</li>
 *   <li><b>Auto-revert</b> — the decision reflects an override only while it is active at the
 *       clock's current instant; once expired the computed result is used automatically.</li>
 * </ul>
 */
@Service
public class RecomputeService {

    private final RiskSnapshotRepository snapshotRepository;
    private final AdvisoryRepository advisoryRepository;
    private final ManualOverrideRepository overrideRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final ThresholdEvaluator thresholdEvaluator;
    private final OverrideResolver overrideResolver;
    private final NotificationComposer notificationComposer;
    private final Clock clock;

    public RecomputeService(RiskSnapshotRepository snapshotRepository,
                            AdvisoryRepository advisoryRepository,
                            ManualOverrideRepository overrideRepository,
                            NotificationOutboxRepository outboxRepository,
                            ThresholdEvaluator thresholdEvaluator,
                            OverrideResolver overrideResolver,
                            NotificationComposer notificationComposer,
                            Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.advisoryRepository = advisoryRepository;
        this.overrideRepository = overrideRepository;
        this.outboxRepository = outboxRepository;
        this.thresholdEvaluator = thresholdEvaluator;
        this.overrideResolver = overrideResolver;
        this.notificationComposer = notificationComposer;
        this.clock = clock;
    }

    /**
     * Recompute and, if the outcome changed, persist a new advisory plus its notification.
     *
     * @return the current advisory after recompute (existing one if nothing changed)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Advisory recompute(String snapshotId) {
        Instant now = clock.instant();

        // Pessimistic lock serializes concurrent recomputes of the same snapshot.
        RiskSnapshot snapshot = snapshotRepository.findByIdForUpdate(snapshotId)
                .orElseThrow(() -> new SnapshotNotFoundException(snapshotId));

        DecisionEvaluation computed = thresholdEvaluator.evaluate(snapshot, now);

        List<ManualOverride> overrides = overrideRepository.findBySnapshotIdOrderByEffectiveFromDesc(snapshotId);
        Optional<ManualOverride> active = overrideResolver.activeOverride(overrides, now);

        DecisionLevel level;
        AdvisorySource source;
        Long overrideId;
        String rationale;
        String requestId;
        if (active.isPresent()) {
            ManualOverride ov = active.get();
            level = ov.getForcedLevel();
            source = AdvisorySource.OVERRIDE;
            overrideId = ov.getId();
            requestId = ov.getRequestId();
            rationale = String.format("人工覆写生效：操作者=%s，理由=%s，有效期至%s。被覆盖的计算结果为%s（%s）。",
                    ov.getOperator(), ov.getReason(), ov.getExpiresAt(), computed.level(), computed.rationale());
        } else {
            level = computed.level();
            source = AdvisorySource.COMPUTED;
            overrideId = null;
            requestId = null;
            rationale = computed.rationale();
        }

        long evidenceVersion = snapshot.getEvidenceVersion();

        // Idempotency: skip writing if the current advisory already reflects this outcome.
        Optional<Advisory> current =
                advisoryRepository.findFirstBySnapshotIdAndSupersededFalseOrderByComputedAtDesc(snapshotId);
        if (current.isPresent() && matches(current.get(), level, source, overrideId, evidenceVersion)) {
            return current.get();
        }

        // Atomic swap: supersede all current advisories, insert the new one + its outbox row.
        advisoryRepository.supersedeCurrent(snapshotId);

        Advisory advisory = new Advisory(snapshotId, snapshot.getRegionCode(), evidenceVersion,
                level, source, overrideId, rationale, computed.affectedPersons(), now);
        advisory = advisoryRepository.saveAndFlush(advisory);

        String payload = notificationComposer.compose(advisory, snapshot, requestId);
        outboxRepository.save(new NotificationOutbox(
                advisory.getId(), snapshotId, evidenceVersion, payload));

        return advisory;
    }

    private boolean matches(Advisory a, DecisionLevel level, AdvisorySource source,
                            Long overrideId, long evidenceVersion) {
        return a.getDecisionLevel() == level
                && a.getSource() == source
                && a.getEvidenceVersion() == evidenceVersion
                && java.util.Objects.equals(a.getOverrideId(), overrideId);
    }
}
