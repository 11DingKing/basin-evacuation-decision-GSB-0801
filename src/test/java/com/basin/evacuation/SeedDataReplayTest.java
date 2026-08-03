package com.basin.evacuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.basin.evacuation.decision.Decision;
import com.basin.evacuation.decision.DecisionOutcome;
import com.basin.evacuation.decision.DecisionService;
import com.basin.evacuation.decision.DecisionSource;
import com.basin.evacuation.notification.OutboxRepository;
import com.basin.evacuation.region.RegionService;
import com.basin.evacuation.snapshot.RiskSnapshot;
import com.basin.evacuation.snapshot.SnapshotService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 种子迁移重放验证：真实 PostgreSQL 上 Flyway V1+V2 必须能完整重建
 * 行政区、初始快照、seq=1 的计算建议与 PENDING outbox。
 */
class SeedDataReplayTest extends PostgresIntegrationTest {

    static final String SEED_SNAPSHOT = "sc-510182-20260729T0300";

    @Autowired
    RegionService regionService;
    @Autowired
    SnapshotService snapshotService;
    @Autowired
    DecisionService decisionService;
    @Autowired
    OutboxRepository outboxRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void seedRegionExists() {
        assertThat(regionService.get("510182").getName()).isEqualTo("彭州市");
        assertThat(regionService.get("510182").getParentCode()).isEqualTo("510100");
    }

    @Test
    void seedSnapshotMatchesRequiredValues() {
        RiskSnapshot s = snapshotService.get(SEED_SNAPSHOT);
        assertThat(s.getRegionCode()).isEqualTo("510182");
        assertThat(s.getRainfall3hMm()).isEqualByComparingTo(new BigDecimal("118.00"));
        assertThat(s.getWaterLevelM()).isEqualByComparingTo(new BigDecimal("6.120"));
        assertThat(s.getHazardPointStatus().name()).isEqualTo("WARNING");
        assertThat(s.getPrimaryRoadStatus().name()).isEqualTo("CLOSED");
        assertThat(s.getSecondaryRoadStatus().name()).isEqualTo("UNKNOWN");
        assertThat(s.getVulnerablePopulation()).isEqualTo(286);
        // 四类上游健康状态齐全
        assertThat(s.getUpstreamHealth()).containsOnlyKeys(
                com.basin.evacuation.snapshot.UpstreamKind.RAINFALL,
                com.basin.evacuation.snapshot.UpstreamKind.WATER_LEVEL,
                com.basin.evacuation.snapshot.UpstreamKind.HAZARD_POINT,
                com.basin.evacuation.snapshot.UpstreamKind.ROAD);
    }

    @Test
    void seedDecisionIsLevel1AndReferencesEvidenceVersion() {
        // 种子建议按确定性 UUID 直接定位（其它测试类可能已追加了重算历史，不依赖 current 仍是 seq=1）
        Decision seed = decisionService.get(java.util.UUID.fromString("a1000000-0000-4000-8000-000000000001"));
        assertThat(seed.getOutcome()).isEqualTo(DecisionOutcome.EVACUATE_NOW);
        assertThat(seed.getSource()).isEqualTo(DecisionSource.COMPUTED);
        assertThat(seed.getSeq()).isEqualTo(1);
        assertThat(seed.getSnapshotId()).isEqualTo(SEED_SNAPSHOT);
        assertThat(seed.getSnapshotVersion()).isEqualTo(1);
        assertThat(seed.getEvidence().snapshotVersion()).isEqualTo(1);
        assertThat(seed.getEvidence().vulnerablePopulation()).isEqualTo(286);
        assertThat(seed.getReasons()).contains(
                "达到一级（立即转移）阈值：3小时累计降水118mm≥100mm且河道水位6.12m≥6m");
        // outbox 与决策一起被重放
        long outboxCount = outboxRepository.countByDecisionIdIn(java.util.List.of(seed.getId()));
        assertThat(outboxCount).isEqualTo(1);
        var message = outboxRepository.findAll().stream()
                .filter(m -> m.getDecisionId().equals(seed.getId()))
                .findFirst().orElseThrow();
        // 投递状态可能被其它测试类的重放推进，这里只验证 outbox 与 payload 被完整重放
        assertThat(message.getPayload().get("snapshotId")).isEqualTo(SEED_SNAPSHOT);
        assertThat(message.getPayload().get("outcome")).isEqualTo("EVACUATE_NOW");
        assertThat(message.getPayload().get("vulnerablePopulation")).isEqualTo(286);

        // 旧快照当前建议始终是 COMPUTED 的一级·立即转移（有效覆写不存在时）
        Decision current = decisionService.current(SEED_SNAPSHOT);
        assertThat(current.getOutcome()).isEqualTo(DecisionOutcome.EVACUATE_NOW);
        assertThat(current.getSource()).isEqualTo(DecisionSource.COMPUTED);
    }

    @Test
    void immutableTablesRejectUpdateAndDelete() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE risk_snapshot SET version = 2 WHERE snapshot_id = ?", SEED_SNAPSHOT))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM decision WHERE snapshot_id = ?", SEED_SNAPSHOT))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM notification_outbox WHERE decision_id = 'a1000000-0000-4000-8000-000000000001'"))
                .hasMessageContaining("immutable");
    }
}
