package com.example.basin.evacuation.application;

import com.example.basin.evacuation.domain.decision.Decision;
import com.example.basin.evacuation.domain.decision.DecisionRepository;
import com.example.basin.evacuation.domain.notification.NotificationService;
import com.example.basin.evacuation.domain.override.ManualOverride;
import com.example.basin.evacuation.domain.override.OverrideService;
import com.example.basin.evacuation.domain.shared.DecisionLevel;
import com.example.basin.evacuation.domain.snapshot.RiskSnapshot;
import com.example.basin.evacuation.domain.snapshot.SnapshotRepository;
import com.example.basin.evacuation.domain.threshold.ThresholdEvaluationService;
import com.example.basin.evacuation.domain.threshold.ThresholdResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Application service that orchestrates the three independent domain modules:
 * {@link ThresholdEvaluationService} (computed level), {@link OverrideService}
 * (manual override / expiry) and {@link NotificationService} (transactional
 * outbox). Controllers call this service; they never encode decision rules.
 *
 * <p>Concurrency: recompute locks the immutable snapshot row with
 * {@code SELECT ... FOR UPDATE}, which serializes concurrent recomputations of
 * the SAME snapshot without blocking different snapshots. The unique
 * {@code (snapshot_id, sequence_no)} constraint is a final backstop.
 *
 * <p>Atomicity: the decision and its outbox rows are inserted in one local
 * transaction, so there is never a half-written state.
 */
@Service
public class DecisionService {

    private final SnapshotRepository snapshotRepository;
    private final DecisionRepository decisionRepository;
    private final ThresholdEvaluationService thresholdService;
    private final OverrideService overrideService;
    private final NotificationService notificationService;
    private final Clock clock;

    public DecisionService(SnapshotRepository snapshotRepository,
                           DecisionRepository decisionRepository,
                           ThresholdEvaluationService thresholdService,
                           OverrideService overrideService,
                           NotificationService notificationService,
                           Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.decisionRepository = decisionRepository;
        this.thresholdService = thresholdService;
        this.overrideService = overrideService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public Decision recompute(String snapshotId) {
        RiskSnapshot snapshot = snapshotRepository.findForUpdate(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("snapshot not found: " + snapshotId));

        ThresholdResult result = thresholdService.evaluate(snapshot);

        ManualOverride activeOverride = overrideService.findActive(snapshotId).orElse(null);
        DecisionLevel effectiveLevel;
        Long overrideId = null;
        String rationale;

        if (activeOverride != null) {
            effectiveLevel = activeOverride.getTargetLevel();
            overrideId = activeOverride.getId();
            rationale = result.rationale()
                    + "；【人工覆写生效】操作者=" + activeOverride.getOperator()
                    + "，覆写为=" + activeOverride.getTargetLevel().label()
                    + "，理由=" + activeOverride.getReason()
                    + "，过期时间=" + activeOverride.getExpiresAt();
        } else {
            effectiveLevel = result.computedLevel();
            rationale = result.rationale();
        }

        int sequenceNo = decisionRepository.maxSequenceNoForSnapshot(snapshotId) + 1;
        Instant now = clock.instant();

        Decision decision = Decision.builder()
                .snapshotId(snapshot.getSnapshotId())
                .districtCode(snapshot.getDistrictCode())
                .level(effectiveLevel)
                .computedLevel(result.computedLevel())
                .activeOverrideId(overrideId)
                .evidenceVersion(snapshot.getEvidenceVersion())
                .rationale(rationale)
                .dimensionBreakdown(result.breakdown())
                .sequenceNo(sequenceNo)
                .createdAt(now)
                .build();

        Decision saved = decisionRepository.save(decision);
        notificationService.createForDecision(saved, snapshot);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Decision> getLatest(String snapshotId) {
        return decisionRepository.findFirstBySnapshotIdOrderBySequenceNoDesc(snapshotId);
    }

    @Transactional(readOnly = true)
    public List<Decision> getHistory(String snapshotId) {
        return decisionRepository.findBySnapshotIdOrderBySequenceNoDesc(snapshotId);
    }

    @Transactional(readOnly = true)
    public Optional<Decision> getById(Long id) {
        return decisionRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Page<Decision> search(String districtCode, DecisionLevel level, Pageable pageable) {
        if (districtCode != null && level != null) {
            return decisionRepository.findByDistrictCodeAndLevelOrderByCreatedAtDesc(districtCode, level, pageable);
        }
        if (districtCode != null) {
            return decisionRepository.findByDistrictCodeOrderByCreatedAtDesc(districtCode, pageable);
        }
        if (level != null) {
            return decisionRepository.findByLevelOrderByCreatedAtDesc(level, pageable);
        }
        return decisionRepository.findAll(pageable);
    }
}
