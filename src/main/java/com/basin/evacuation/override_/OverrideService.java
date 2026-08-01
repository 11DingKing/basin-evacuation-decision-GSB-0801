package com.basin.evacuation.override_;

import com.basin.evacuation.common.BadRequestException;
import com.basin.evacuation.common.NotFoundException;
import com.basin.evacuation.decision.Decision;
import com.basin.evacuation.decision.DecisionEvidence;
import com.basin.evacuation.decision.DecisionOutcome;
import com.basin.evacuation.decision.DecisionRepository;
import com.basin.evacuation.decision.DecisionSource;
import com.basin.evacuation.notification.NotificationService;
import com.basin.evacuation.snapshot.RiskSnapshot;
import com.basin.evacuation.snapshot.RiskSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 人工覆写服务（独立领域模块）。覆写 = 一条 manual_override 记录 + 一条 source=OVERRIDE 的建议，
 * 同一事务内写 outbox；历史计算建议原样保留，到期后自动恢复生效。
 */
@Service
public class OverrideService {

    private final ManualOverrideRepository overrides;
    private final DecisionRepository decisions;
    private final RiskSnapshotRepository snapshots;
    private final NotificationService notifications;
    private final Clock clock;

    public OverrideService(ManualOverrideRepository overrides,
                           DecisionRepository decisions,
                           RiskSnapshotRepository snapshots,
                           NotificationService notifications,
                           Clock clock) {
        this.overrides = overrides;
        this.decisions = decisions;
        this.snapshots = snapshots;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public Decision recordOverride(String snapshotId, DecisionOutcome outcome,
                                   String operator, String reason, Instant expiresAt) {
        if (outcome == null || !outcome.isRiskLevel()) {
            throw new BadRequestException("覆写目标必须是四个风险等级之一（EVACUATE_NOW/PRE_TRANSFER/PREPARE/LOW_RISK）");
        }
        if (operator == null || operator.isBlank()) {
            throw new BadRequestException("覆写必须包含操作者 operator");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("覆写必须包含理由 reason");
        }
        Instant now = Instant.now(clock);
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw new BadRequestException("覆写必须包含晚于当前时间的过期时间 expiresAt");
        }

        RiskSnapshot snapshot = snapshots.lockBySnapshotId(snapshotId)
                .orElseThrow(() -> new NotFoundException("快照不存在: " + snapshotId));

        ManualOverride override = overrides.save(new ManualOverride(
                UUID.randomUUID(), snapshotId, outcome, operator.strip(), reason.strip(), now, expiresAt));

        int seq = decisions.findMaxSeq(snapshotId).orElse(0) + 1;
        List<String> reasons = List.of(
                "人工覆写为" + outcome.label(),
                "操作者：" + operator.strip(),
                "理由：" + reason.strip(),
                "过期时间：" + expiresAt);
        Decision decision = new Decision(
                UUID.randomUUID(), snapshotId, snapshot.getVersion(), snapshot.getRegionCode(), seq,
                outcome, DecisionSource.OVERRIDE, override.getId(),
                reasons, DecisionEvidence.from(snapshot), now);
        decisions.save(decision);
        notifications.enqueue(decision, snapshot);
        return decision;
    }

    @Transactional(readOnly = true)
    public List<ManualOverride> list(String snapshotId) {
        return overrides.findBySnapshotIdOrderByCreatedAtDesc(snapshotId);
    }
}
