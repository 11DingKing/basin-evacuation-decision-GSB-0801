package com.basin.evacuation;

import static org.assertj.core.api.Assertions.assertThat;

import com.basin.evacuation.decision.Decision;
import com.basin.evacuation.decision.DecisionOutcome;
import com.basin.evacuation.decision.DecisionService;
import com.basin.evacuation.notification.OutboxRepository;
import com.basin.evacuation.notification.OutboxStatus;
import com.basin.evacuation.snapshot.HazardPointStatus;
import com.basin.evacuation.snapshot.RoadStatus;
import com.basin.evacuation.snapshot.SnapshotService;
import com.basin.evacuation.snapshot.UpstreamHealth;
import com.basin.evacuation.snapshot.UpstreamKind;
import com.basin.evacuation.snapshot.UpstreamStatus;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 上游降级场景：四类上游里两类超时（降雨、水位）、一类返回旧版本（隐患点）。
 * 核心证据缺失 -> 数据不足（区别于低风险）；旧版本写入健康状态并体现在理由中；
 * 建议与 outbox 仍然同事务落库。
 */
class DegradedUpstreamTest extends PostgresIntegrationTest {

    @Autowired
    SnapshotService snapshotService;
    @Autowired
    DecisionService decisionService;
    @Autowired
    OutboxRepository outboxRepository;

    @Test
    void twoTimeoutsAndOneStaleYieldInsufficientData() {
        Map<UpstreamKind, UpstreamHealth> health = new EnumMap<>(UpstreamKind.class);
        health.put(UpstreamKind.RAINFALL, new UpstreamHealth(UpstreamStatus.TIMEOUT, null, "connect timeout"));
        health.put(UpstreamKind.WATER_LEVEL, new UpstreamHealth(UpstreamStatus.TIMEOUT, null, "read timeout"));
        health.put(UpstreamKind.HAZARD_POINT, new UpstreamHealth(UpstreamStatus.STALE, "hz-20260728T2100", null));
        health.put(UpstreamKind.ROAD, new UpstreamHealth(UpstreamStatus.OK, "road-20260729T0300", null));

        String snapshotId = "t-degraded-1";
        snapshotService.create(snapshotId, "510182", 1,
                null, null, // 降雨、水位上游超时：本次无数据
                HazardPointStatus.WARNING, RoadStatus.OPEN, RoadStatus.OPEN,
                12, health, Instant.parse("2026-08-01T02:30:00Z"), null);

        Decision current = decisionService.current(snapshotId);
        // 数据不足与低风险是两种完全不同的结果
        assertThat(current.getOutcome()).isEqualTo(DecisionOutcome.INSUFFICIENT_DATA);
        assertThat(current.getOutcome()).isNotEqualTo(DecisionOutcome.LOW_RISK);
        assertThat(current.getOutcome().isRiskLevel()).isFalse();
        assertThat(current.getReasons().get(0))
                .contains("核心证据缺失").contains("3小时累计降水").contains("河道水位");
        assertThat(current.getReasons()).anySatisfy(r ->
                assertThat(r).contains("上游隐患点返回旧版本数据（version=hz-20260728T2100）"));
        assertThat(current.getReasons()).anySatisfy(r -> assertThat(r).contains("上游降雨超时"));

        // 证据里冻结了四类上游的健康状态
        assertThat(current.getEvidence().upstreamHealth().get(UpstreamKind.RAINFALL).status())
                .isEqualTo(UpstreamStatus.TIMEOUT);
        assertThat(current.getEvidence().upstreamHealth().get(UpstreamKind.HAZARD_POINT).version())
                .isEqualTo("hz-20260728T2100");

        // 建议与 outbox 同事务写入：数据不足的建议同样可通知、可重放
        var messages = outboxRepository.findAll().stream()
                .filter(m -> m.getDecisionId().equals(current.getId()))
                .toList();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(messages.get(0).getPayload().get("outcome")).isEqualTo("INSUFFICIENT_DATA");
    }
}
