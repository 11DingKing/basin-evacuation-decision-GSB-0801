package com.basin.evacuation.override_;

import com.basin.evacuation.common.BadRequestException;
import com.basin.evacuation.common.ConflictException;
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
 *
 * 幂等：requestId 是业务请求号。持快照行锁期间先按请求号查重 ——
 * 相同请求号且内容一致直接返回已有建议（不生成新 UUID/建议/outbox）；
 * 相同请求号但内容不一致返回 409。
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
    public Decision recordOverride(String snapshotId, String requestId, DecisionOutcome outcome,
                                   String operator, String reason, Instant expiresAt) {
        if (requestId == null || requestId.isBlank()) {
            throw new BadRequestException("覆写必须包含业务请求号 requestId（幂等依据）");
        }
        if (outcome == null || !outcome.isRiskLevel()) {
            throw new BadRequestException("覆写目标必须是四个风险等级之一（EVACUATE_NOW/PRE_TRANSFER/PREPARE/LOW_RISK）");
        }
        if (operator == null || operator.isBlank()) {
            throw new BadRequestException("覆写必须包含操作者 operator");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("覆写必须包含理由 reason");
        }
        if (expiresAt == null) {
            throw new BadRequestException("覆写必须包含过期时间 expiresAt");
        }
        String reqId = requestId.strip();
        String op = operator.strip();
        String rs = reason.strip();

        RiskSnapshot snapshot = snapshots.lockBySnapshotId(snapshotId)
                .orElseThrow(() -> new NotFoundException("快照不存在: " + snapshotId));

        // 幂等重放：持锁查重，相同请求号直接返回已有建议
        var existing = overrides.findByRequestId(reqId);
        if (existing.isPresent()) {
            ManualOverride ov = existing.get();
            boolean same = ov.getSnapshotId().equals(snapshotId)
                    && ov.getOutcome() == outcome
                    && ov.getOperator().equals(op)
                    && ov.getReason().equals(rs)
                    && ov.getExpiresAt().equals(expiresAt);
            if (!same) {
                throw new ConflictException("请求号已存在且内容不一致: " + reqId);
            }
            return decisions.findByOverrideId(ov.getId())
                    .orElseThrow(() -> new NotFoundException("覆写对应的建议不存在: " + ov.getId()));
        }

        Instant now = Instant.now(clock);
        if (!expiresAt.isAfter(now)) {
            throw new BadRequestException("过期时间必须晚于当前时间");
        }

        ManualOverride override = overrides.save(new ManualOverride(
                UUID.randomUUID(), snapshotId, reqId, outcome, op, rs, now, expiresAt));

        int seq = decisions.findMaxSeq(snapshotId).orElse(0) + 1;
        List<String> reasons = List.of(
                "人工覆写为" + outcome.label(),
                "操作者：" + op,
                "理由：" + rs,
                "过期时间：" + expiresAt,
                "业务请求号：" + reqId);
        Decision decision = new Decision(
                UUID.randomUUID(), snapshotId, snapshot.getVersion(), snapshot.getRegionCode(), seq,
                outcome, DecisionSource.OVERRIDE, override.getId(), reqId,
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
