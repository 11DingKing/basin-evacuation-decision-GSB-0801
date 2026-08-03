package cn.gov.basin.evacuation.domain.threshold;

import cn.gov.basin.evacuation.domain.decision.DecisionLevel;
import cn.gov.basin.evacuation.domain.snapshot.HazardStatus;
import cn.gov.basin.evacuation.domain.snapshot.RiskSnapshotView;
import cn.gov.basin.evacuation.domain.snapshot.RoadStatus;
import cn.gov.basin.evacuation.domain.snapshot.UpstreamHealth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ThresholdEvaluationServiceTest {

    private final ThresholdEvaluationService service = new ThresholdEvaluationService();

    @Test
    @DisplayName("LEVEL_1：所有指标低于 L2 阈值且所有上游 OK")
    void level1AllClear() {
        RiskSnapshotView s = base()
                .rain(BigDecimal.valueOf(49.99))
                .water(BigDecimal.valueOf(4.99))
                .hazard(HazardStatus.NORMAL)
                .primary(RoadStatus.OPEN)
                .build();

        EvaluationResult r = service.evaluate(s);

        assertThat(r.level()).isEqualTo(DecisionLevel.LEVEL_1);
        assertThat(r.triggeredRules()).isEmpty();
        assertThat(r.missingEvidence()).isEmpty();
        assertThat(r.isInsufficient()).isFalse();
    }

    @Test
    @DisplayName("LEVEL_1 边界：50.00/5.00 恰好触发 LEVEL_2")
    void level2Boundary() {
        RiskSnapshotView s = base()
                .rain(BigDecimal.valueOf(50.00))
                .water(BigDecimal.valueOf(4.99))
                .build();
        assertThat(service.evaluate(s).level()).isEqualTo(DecisionLevel.LEVEL_2);

        RiskSnapshotView s2 = base()
                .rain(BigDecimal.valueOf(49.99))
                .water(BigDecimal.valueOf(5.00))
                .build();
        assertThat(service.evaluate(s2).level()).isEqualTo(DecisionLevel.LEVEL_2);
    }

    @Test
    @DisplayName("LEVEL_2 边界：80.00/5.50 恰好触发 LEVEL_3，WARNING 隐患")
    void level3Boundary() {
        RiskSnapshotView s = base()
                .rain(BigDecimal.valueOf(80.00))
                .water(BigDecimal.valueOf(4.99))
                .build();
        assertThat(service.evaluate(s).level()).isEqualTo(DecisionLevel.LEVEL_3);

        RiskSnapshotView s2 = base()
                .rain(BigDecimal.valueOf(10))
                .water(BigDecimal.valueOf(5.50))
                .hazard(HazardStatus.WARNING)
                .build();
        assertThat(service.evaluate(s2).level()).isEqualTo(DecisionLevel.LEVEL_3);
    }

    @Test
    @DisplayName("LEVEL_4 边界：100.00/6.00/CRITICAL 任一触发即四级")
    void level4Boundary() {
        assertThat(service.evaluate(base().rain(BigDecimal.valueOf(100.00)).build()).level())
                .isEqualTo(DecisionLevel.LEVEL_4);
        assertThat(service.evaluate(base().rain(BigDecimal.valueOf(99.99))
                .water(BigDecimal.valueOf(6.00)).build()).level())
                .isEqualTo(DecisionLevel.LEVEL_4);
        assertThat(service.evaluate(base().rain(BigDecimal.valueOf(10))
                .water(BigDecimal.valueOf(4.0))
                .hazard(HazardStatus.CRITICAL).build()).level())
                .isEqualTo(DecisionLevel.LEVEL_4);
    }

    @Test
    @DisplayName("初始快照 118mm/6.12m/WARNING/CLOSED -> LEVEL_4")
    void initialSnapshotIsLevel4() {
        RiskSnapshotView s = base()
                .snapshotId("sc-510182-20260729T0300")
                .rain(BigDecimal.valueOf(118.00))
                .water(BigDecimal.valueOf(6.12))
                .hazard(HazardStatus.WARNING)
                .primary(RoadStatus.CLOSED)
                .secondary(RoadStatus.UNKNOWN)
                .vulnerable(286)
                .build();

        EvaluationResult r = service.evaluate(s);

        assertThat(r.level()).isEqualTo(DecisionLevel.LEVEL_4);
        assertThat(r.triggeredRules())
                .contains("RAIN_3H_GE_100", "WATER_LEVEL_GE_6_00", "HAZARD_WARNING");
        assertThat(r.evidenceRef().snapshotId()).isEqualTo("sc-510182-20260729T0300");
    }

    @Test
    @DisplayName("数据不足：降雨上游 TIMEOUT，与低风险完全区分")
    void insufficientWhenRainfallTimeout() {
        RiskSnapshotView s = base()
                .rain(BigDecimal.valueOf(10))
                .water(BigDecimal.valueOf(4.0))
                .rainfallHealth(UpstreamHealth.TIMEOUT)
                .build();

        EvaluationResult r = service.evaluate(s);
        assertThat(r.level()).isEqualTo(DecisionLevel.INSUFFICIENT_DATA);
        assertThat(r.missingEvidence()).contains("RAINFALL_UNAVAILABLE");
    }

    @Test
    @DisplayName("数据不足：水位 ERROR 与降雨 STALE 同时发生也判不足")
    void insufficientWhenWaterError() {
        RiskSnapshotView s = base()
                .rain(BigDecimal.valueOf(10))
                .water(BigDecimal.valueOf(4.0))
                .waterHealth(UpstreamHealth.ERROR)
                .build();
        assertThat(service.evaluate(s).level()).isEqualTo(DecisionLevel.INSUFFICIENT_DATA);
    }

    @Test
    @DisplayName("低风险与数据不足不混淆：所有核心可用但数值低 -> LEVEL_1，而非 INSUFFICIENT_DATA")
    void lowRiskIsNotInsufficient() {
        RiskSnapshotView s = base()
                .rain(BigDecimal.valueOf(1.0))
                .water(BigDecimal.valueOf(1.0))
                .hazard(HazardStatus.NORMAL)
                .primary(RoadStatus.OPEN)
                .build();
        EvaluationResult r = service.evaluate(s);
        assertThat(r.level()).isEqualTo(DecisionLevel.LEVEL_1);
        assertThat(r.isInsufficient()).isFalse();
    }

    @Test
    @DisplayName("主路 closed 即使雨/水低，至少 LEVEL_2")
    void primaryRoadClosedRaisesToLevel2() {
        RiskSnapshotView s = base()
                .rain(BigDecimal.valueOf(5))
                .water(BigDecimal.valueOf(3.0))
                .hazard(HazardStatus.NORMAL)
                .primary(RoadStatus.CLOSED)
                .build();
        assertThat(service.evaluate(s).level()).isEqualTo(DecisionLevel.LEVEL_2);
        assertThat(service.evaluate(s).triggeredRules()).contains("PRIMARY_ROAD_CLOSED");
    }

    private static SnapshotBuilder base() {
        return new SnapshotBuilder();
    }

    private static final class SnapshotBuilder {
        private String snapshotId = "snap-test";
        private BigDecimal rain = BigDecimal.ZERO;
        private BigDecimal water = BigDecimal.ZERO;
        private HazardStatus hazard = HazardStatus.NORMAL;
        private RoadStatus primary = RoadStatus.OPEN;
        private RoadStatus secondary = RoadStatus.OPEN;
        private Integer vulnerable = 0;
        private UpstreamHealth rainHealth = UpstreamHealth.OK;
        private UpstreamHealth waterHealth = UpstreamHealth.OK;
        private UpstreamHealth hazardHealth = UpstreamHealth.OK;
        private UpstreamHealth infraHealth = UpstreamHealth.OK;

        SnapshotBuilder snapshotId(String id) { this.snapshotId = id; return this; }
        SnapshotBuilder rain(BigDecimal v) { this.rain = v; return this; }
        SnapshotBuilder water(BigDecimal v) { this.water = v; return this; }
        SnapshotBuilder hazard(HazardStatus v) { this.hazard = v; return this; }
        SnapshotBuilder primary(RoadStatus v) { this.primary = v; return this; }
        SnapshotBuilder secondary(RoadStatus v) { this.secondary = v; return this; }
        SnapshotBuilder vulnerable(Integer v) { this.vulnerable = v; return this; }
        SnapshotBuilder rainfallHealth(UpstreamHealth h) { this.rainHealth = h; return this; }
        SnapshotBuilder waterHealth(UpstreamHealth h) { this.waterHealth = h; return this; }

        RiskSnapshotView build() {
            return new RiskSnapshotView() {
                public String getSnapshotId() { return snapshotId; }
                public BigDecimal getRainfall3hMm() { return rain; }
                public BigDecimal getWaterLevelM() { return water; }
                public HazardStatus getHazardStatus() { return hazard; }
                public RoadStatus getPrimaryRoadStatus() { return primary; }
                public RoadStatus getSecondaryRoadStatus() { return secondary; }
                public Integer getVulnerablePopulation() { return vulnerable; }
                public UpstreamHealth getRainfallHealth() { return rainHealth; }
                public UpstreamHealth getWaterLevelHealth() { return waterHealth; }
                public UpstreamHealth getHazardHealth() { return hazardHealth; }
                public UpstreamHealth getInfrastructureHealth() { return infraHealth; }
                public String getRainfallVersion() { return "rv"; }
                public String getWaterLevelVersion() { return "wv"; }
                public String getHazardVersion() { return "hv"; }
                public String getInfrastructureVersion() { return "iv"; }
            };
        }
    }
}
