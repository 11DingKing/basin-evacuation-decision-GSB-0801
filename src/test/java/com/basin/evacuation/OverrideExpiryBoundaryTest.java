package com.basin.evacuation;

import static org.assertj.core.api.Assertions.assertThat;

import com.basin.evacuation.decision.Decision;
import com.basin.evacuation.decision.DecisionOutcome;
import com.basin.evacuation.decision.DecisionService;
import com.basin.evacuation.decision.DecisionSource;
import com.basin.evacuation.override_.OverrideService;
import com.basin.evacuation.snapshot.HazardPointStatus;
import com.basin.evacuation.snapshot.RoadStatus;
import com.basin.evacuation.snapshot.SnapshotService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 可注入时钟的覆写边界测试：生效前一毫秒、恰好到期、到期后一毫秒。
 * 语义：now < expiresAt 生效；now >= expiresAt 视为过期，自动恢复计算结果。
 */
class OverrideExpiryBoundaryTest extends PostgresIntegrationTest {

    @Autowired
    SnapshotService snapshotService;
    @Autowired
    DecisionService decisionService;
    @Autowired
    OverrideService overrideService;
    @Autowired
    MutableClock clock;

    @Test
    void overrideExpiresExactlyAtExpiresAt() {
        Instant base = Instant.parse("2026-08-01T02:00:00Z");
        clock.set(base);

        String snapshotId = "t-override-boundary-1";
        snapshotService.create(snapshotId, "510182", 1,
                new BigDecimal("10.00"), new BigDecimal("4.500"),
                HazardPointStatus.OK, RoadStatus.OPEN, RoadStatus.OPEN,
                0, allOkUpstream(), base, null);

        // 初始为计算结果：低风险
        Decision before = decisionService.current(snapshotId);
        assertThat(before.getOutcome()).isEqualTo(DecisionOutcome.LOW_RISK);
        assertThat(before.getSource()).isEqualTo(DecisionSource.COMPUTED);

        Instant expiresAt = base.plusSeconds(3600);
        overrideService.recordOverride(snapshotId, "ov-boundary-1", DecisionOutcome.EVACUATE_NOW,
                "值班员张三", "上游来水突增，提前组织转移", expiresAt);

        // 生效前一毫秒：覆写生效
        clock.set(expiresAt.minusMillis(1));
        Decision active = decisionService.current(snapshotId);
        assertThat(active.getOutcome()).isEqualTo(DecisionOutcome.EVACUATE_NOW);
        assertThat(active.getSource()).isEqualTo(DecisionSource.OVERRIDE);

        // 恰好到期：视为过期，恢复计算结果
        clock.set(expiresAt);
        Decision atExpiry = decisionService.current(snapshotId);
        assertThat(atExpiry.getOutcome()).isEqualTo(DecisionOutcome.LOW_RISK);
        assertThat(atExpiry.getSource()).isEqualTo(DecisionSource.COMPUTED);

        // 到期后一毫秒：仍为计算结果
        clock.set(expiresAt.plusMillis(1));
        assertThat(decisionService.current(snapshotId).getOutcome()).isEqualTo(DecisionOutcome.LOW_RISK);

        // 历史建议与覆写都不可删除：2 条建议（计算 + 覆写）、1 条覆写记录都在
        assertThat(decisionService.history(snapshotId)).hasSize(2);
        assertThat(overrideService.list(snapshotId)).hasSize(1);
        assertThat(overrideService.list(snapshotId).get(0).getOperator()).isEqualTo("值班员张三");
        assertThat(overrideService.list(snapshotId).get(0).getReason()).contains("上游来水突增");
        assertThat(overrideService.list(snapshotId).get(0).getExpiresAt()).isEqualTo(expiresAt);
    }
}
