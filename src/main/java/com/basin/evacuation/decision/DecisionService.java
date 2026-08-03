package com.basin.evacuation.decision;

import com.basin.evacuation.common.ConflictException;
import com.basin.evacuation.common.NotFoundException;
import com.basin.evacuation.notification.NotificationService;
import com.basin.evacuation.override_.ManualOverrideRepository;
import com.basin.evacuation.snapshot.RiskSnapshot;
import com.basin.evacuation.snapshot.RiskSnapshotRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 决策服务：阈值评估的落地、并发安全重算、当前生效建议的解析。
 * 本类不含决策规则（规则只在 ThresholdEvaluator）。
 */
@Service
public class DecisionService {

    private final DecisionRepository decisions;
    private final RiskSnapshotRepository snapshots;
    private final ManualOverrideRepository overrides;
    private final ThresholdEvaluator evaluator;
    private final NotificationService notifications;
    private final Clock clock;

    public DecisionService(DecisionRepository decisions,
                           RiskSnapshotRepository snapshots,
                           ManualOverrideRepository overrides,
                           ThresholdEvaluator evaluator,
                           NotificationService notifications,
                           Clock clock) {
        this.decisions = decisions;
        this.snapshots = snapshots;
        this.overrides = overrides;
        this.evaluator = evaluator;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * 计算并追加一条 COMPUTED 建议（同一事务内同时写 outbox）。
     * 快照行悲观锁串行化并发重算；(snapshot_id, seq) 唯一约束兜底。
     * requestId 为幂等依据：持锁期间先查重，相同请求号直接返回已有建议，
     * 并发重放/重试不会产生新 UUID、新建议或新 outbox。
     */
    @Transactional
    public Decision computeAndRecord(String snapshotId, String requestId) {
        RiskSnapshot snapshot = snapshots.lockBySnapshotId(snapshotId)
                .orElseThrow(() -> new NotFoundException("快照不存在: " + snapshotId));
        if (requestId != null && !requestId.isBlank()) {
            var existing = decisions.findByRequestId(requestId.strip());
            if (existing.isPresent()) {
                Decision d = existing.get();
                if (!d.getSnapshotId().equals(snapshotId)) {
                    throw new ConflictException("请求号已被其它快照使用: " + requestId);
                }
                return d;
            }
        }
        EvaluationResult result = evaluator.evaluate(snapshot);
        int seq = decisions.findMaxSeq(snapshotId).orElse(0) + 1;
        Decision decision = new Decision(
                UUID.randomUUID(), snapshotId, snapshot.getVersion(), snapshot.getRegionCode(), seq,
                result.outcome(), DecisionSource.COMPUTED, null, normalize(requestId),
                result.reasons(), DecisionEvidence.from(snapshot), Instant.now(clock));
        decisions.save(decision);
        notifications.enqueue(decision, snapshot);
        return decision;
    }

    @Transactional
    public Decision recompute(String snapshotId) {
        return computeAndRecord(snapshotId, null);
    }

    @Transactional
    public Decision recompute(String snapshotId, String requestId) {
        return computeAndRecord(snapshotId, requestId);
    }

    private static String normalize(String requestId) {
        return requestId == null || requestId.isBlank() ? null : requestId.strip();
    }

    /**
     * 当前生效建议：存在未过期覆写（now < expiresAt）时用覆写建议，
     * 否则回退到最新一条 COMPUTED 建议。覆写到期后自动恢复计算结果，无需任何清理任务。
     */
    @Transactional(readOnly = true)
    public Decision current(String snapshotId) {
        Instant now = Instant.now(clock);
        var activeOverride = overrides
                .findFirstBySnapshotIdAndExpiresAtGreaterThanOrderByCreatedAtDesc(snapshotId, now);
        if (activeOverride.isPresent()) {
            return decisions.findByOverrideId(activeOverride.get().getId())
                    .orElseThrow(() -> new NotFoundException("覆写对应的建议不存在: " + activeOverride.get().getId()));
        }
        return decisions.findFirstBySnapshotIdAndSourceOrderBySeqDesc(snapshotId, DecisionSource.COMPUTED)
                .orElseThrow(() -> new NotFoundException("快照暂无建议: " + snapshotId));
    }

    @Transactional(readOnly = true)
    public Decision get(UUID id) {
        return decisions.findById(id)
                .orElseThrow(() -> new NotFoundException("建议不存在: " + id));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Decision> findByRequestId(String requestId) {
        return decisions.findByRequestId(requestId);
    }

    @Transactional(readOnly = true)
    public List<Decision> history(String snapshotId) {
        return decisions.findBySnapshotIdOrderBySeqDesc(snapshotId);
    }

    @Transactional(readOnly = true)
    public Page<Decision> search(String regionCode, String snapshotId, DecisionOutcome outcome,
                                 Instant from, Instant to, Pageable pageable) {
        Specification<Decision> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (regionCode != null && !regionCode.isBlank()) {
                predicates.add(cb.equal(root.get("regionCode"), regionCode));
            }
            if (snapshotId != null && !snapshotId.isBlank()) {
                predicates.add(cb.equal(root.get("snapshotId"), snapshotId));
            }
            if (outcome != null) {
                predicates.add(cb.equal(root.get("outcome"), outcome));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            query.orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("seq")));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return decisions.findAll(spec, pageable);
    }
}
