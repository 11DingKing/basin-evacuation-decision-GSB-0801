package cn.gov.basin.evacuation.decision;

import cn.gov.basin.evacuation.domain.threshold.EvaluationResult;
import cn.gov.basin.evacuation.domain.threshold.ThresholdEvaluationService;
import cn.gov.basin.evacuation.notification.NotificationPort;
import cn.gov.basin.evacuation.override.OverrideService;
import cn.gov.basin.evacuation.snapshot.RiskSnapshot;
import cn.gov.basin.evacuation.snapshot.SnapshotService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class DecisionService {

    private final SnapshotService snapshotService;
    private final ThresholdEvaluationService thresholdService;
    private final OverrideService overrideService;
    private final DecisionAdviceRepository adviceRepository;
    private final AdvisoryLockRepository lockRepository;
    private final NotificationPort notificationPort;
    private final Clock clock;

    public DecisionService(SnapshotService snapshotService,
                           ThresholdEvaluationService thresholdService,
                           OverrideService overrideService,
                           DecisionAdviceRepository adviceRepository,
                           AdvisoryLockRepository lockRepository,
                           NotificationPort notificationPort,
                           Clock clock) {
        this.snapshotService = snapshotService;
        this.thresholdService = thresholdService;
        this.overrideService = overrideService;
        this.adviceRepository = adviceRepository;
        this.lockRepository = lockRepository;
        this.notificationPort = notificationPort;
        this.clock = clock;
    }

    @Transactional
    public DecisionAdvice computeForSnapshot(String snapshotId) {
        return computeForSnapshot(snapshotId, null);
    }

    @Transactional
    public DecisionAdvice computeForSnapshot(String snapshotId, String requestId) {
        lockRepository.acquireSnapshotLock(snapshotId);

        if (requestId != null && !requestId.isBlank()) {
            var existing = adviceRepository
                    .findFirstBySnapshotIdAndRequestIdOrderByComputedAtDesc(snapshotId, requestId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        RiskSnapshot snapshot = snapshotService.get(snapshotId);
        EvaluationResult evaluation = thresholdService.evaluate(snapshot);

        AdviceSource source = AdviceSource.COMPUTED;
        cn.gov.basin.evacuation.domain.decision.DecisionLevel finalLevel = evaluation.level();
        Long overrideId = null;
        String message = evaluation.message();

        Optional<OverrideService.ActiveOverride> active =
                overrideService.findActiveOverride(snapshot.getRegionCode());
        if (active.isPresent()) {
            var o = active.get();
            source = AdviceSource.OVERRIDDEN;
            finalLevel = o.targetLevel();
            overrideId = o.overrideId();
            message = String.format(
                    "[人工覆写 by %s，理由: %s，过期: %s] %s",
                    o.operator(), o.reason(), o.expiresAt(), evaluation.message()
            );
        }

        Instant now = clock.instant();
        Map<String, Object> evidence = buildEvidence(evaluation, source, overrideId, requestId);
        DecisionAdvice advice = new DecisionAdvice(
                snapshot.getSnapshotId(),
                snapshot.getRegionCode(),
                finalLevel,
                source,
                message,
                evidence,
                overrideId,
                now,
                now,
                requestId
        );
        DecisionAdvice saved = adviceRepository.save(advice);

        Map<String, Object> payload = buildNotificationPayload(saved, evaluation, snapshot, requestId);
        notificationPort.enqueue(saved, payload, requestId);

        return saved;
    }

    @Transactional(readOnly = true)
    public DecisionAdvice get(Long id) {
        return adviceRepository.findById(id)
                .orElseThrow(() -> new AdviceNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Optional<DecisionAdvice> latestForSnapshot(String snapshotId) {
        return adviceRepository.findFirstBySnapshotIdOrderByComputedAtDesc(snapshotId);
    }

    @Transactional(readOnly = true)
    public Optional<DecisionAdvice> findLatestForSnapshotAndRequest(String snapshotId, String requestId) {
        if (requestId == null) {
            return Optional.empty();
        }
        return adviceRepository.findFirstBySnapshotIdAndRequestIdOrderByComputedAtDesc(snapshotId, requestId);
    }

    @Transactional(readOnly = true)
    public Page<DecisionAdvice> search(String regionCode,
                                       cn.gov.basin.evacuation.domain.decision.DecisionLevel level,
                                       int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        if (regionCode != null && !regionCode.isBlank() && level != null) {
            return adviceRepository.findByRegionAndLevel(regionCode, level, pageable);
        }
        if (regionCode != null && !regionCode.isBlank()) {
            return adviceRepository.findByRegionCodeOrderByComputedAtDesc(regionCode, pageable);
        }
        return adviceRepository.findLatest(pageable);
    }

    private Map<String, Object> buildEvidence(EvaluationResult evaluation,
                                              AdviceSource source,
                                              Long overrideId,
                                              String requestId) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.putAll(EvidenceRefMapper.toMap(evaluation.evidenceRef()));
        evidence.put("triggeredRules", evaluation.triggeredRules());
        evidence.put("missingEvidence", evaluation.missingEvidence());
        evidence.put("source", source.name());
        if (overrideId != null) {
            evidence.put("overrideId", overrideId);
        }
        if (requestId != null) {
            evidence.put("requestId", requestId);
        }
        return evidence;
    }

    private Map<String, Object> buildNotificationPayload(DecisionAdvice advice,
                                                        EvaluationResult evaluation,
                                                        RiskSnapshot snapshot,
                                                        String requestId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("snapshotId", advice.getSnapshotId());
        payload.put("adviceId", advice.getId());
        payload.put("regionCode", advice.getRegionCode());
        payload.put("decisionLevel", advice.getDecisionLevel().name());
        payload.put("source", advice.getSource().name());
        payload.put("adviceText", advice.getAdviceText());
        payload.put("vulnerablePopulation", snapshot.getVulnerablePopulation());
        payload.put("populationUnit", "PERSON");
        payload.put("triggeredRules", evaluation.triggeredRules());
        payload.put("missingEvidence", evaluation.missingEvidence());
        payload.put("requestId", requestId);
        payload.put("evidenceVersions", Map.of(
                "rainfall", snapshot.getRainfallVersion(),
                "waterLevel", snapshot.getWaterLevelVersion(),
                "hazard", snapshot.getHazardVersion(),
                "infrastructure", snapshot.getInfrastructureVersion()
        ));
        return payload;
    }
}
