package com.basin.evacuation;

import static org.assertj.core.api.Assertions.assertThat;

import com.basin.evacuation.config.ThresholdProperties;
import com.basin.evacuation.decision.DecisionOutcome;
import com.basin.evacuation.decision.EvaluationResult;
import com.basin.evacuation.decision.ThresholdEvaluator;
import com.basin.evacuation.snapshot.HazardPointStatus;
import com.basin.evacuation.snapshot.RiskSnapshot;
import com.basin.evacuation.snapshot.RoadStatus;
import com.basin.evacuation.snapshot.UpstreamHealth;
import com.basin.evacuation.snapshot.UpstreamKind;
import com.basin.evacuation.snapshot.UpstreamStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 阈值评估器单元测试：每个等级的边界值，以及「数据不足」与「低风险」的区分。 */
class ThresholdEvaluatorTest {

    private static final Instant T = Instant.parse("2026-07-28T19:00:00Z");

    private final ThresholdEvaluator evaluator = new ThresholdEvaluator(new ThresholdProperties(
            new ThresholdProperties.Rainfall(bd("100.0"), bd("50.0"), bd("25.0")),
            new ThresholdProperties.WaterLevel(bd("6.0"), bd("5.5"), bd("5.0"))));

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private RiskSnapshot snap(BigDecimal rain, BigDecimal water, HazardPointStatus hazard,
                              RoadStatus primary, RoadStatus secondary) {
        return snap(rain, water, hazard, primary, secondary, 0, Map.of());
    }

    private RiskSnapshot snap(BigDecimal rain, BigDecimal water, HazardPointStatus hazard,
                              RoadStatus primary, RoadStatus secondary, int population,
                              Map<UpstreamKind, UpstreamHealth> health) {
        return new RiskSnapshot("t-unit", "510182", 1, rain, water, hazard, primary, secondary,
                population, health, T, T);
    }

    @Test
    void seedLikeDataYieldsEvacuateNowWithExactReasons() {
        EvaluationResult r = evaluator.evaluate(snap(bd("118.00"), bd("6.120"),
                HazardPointStatus.WARNING, RoadStatus.CLOSED, RoadStatus.UNKNOWN, 286, Map.of()));

        assertThat(r.outcome()).isEqualTo(DecisionOutcome.EVACUATE_NOW);
        assertThat(r.outcome().priority()).isEqualTo(1);
        assertThat(r.reasons()).containsExactly(
                "达到一级（立即转移）阈值：3小时累计降水118mm≥100mm且河道水位6.12m≥6m",
                "主路封闭：撤离路线须避开主路",
                "次路状态未知：需人工核实备用路线",
                "脆弱人群286人：须优先安排转运");
    }

    @Test
    void boundary_rainExactly100AndWaterExactly6_isEvacuateNow() {
        EvaluationResult r = evaluator.evaluate(snap(bd("100.00"), bd("6.000"),
                HazardPointStatus.WARNING, RoadStatus.OPEN, RoadStatus.OPEN));
        assertThat(r.outcome()).isEqualTo(DecisionOutcome.EVACUATE_NOW);
    }

    @Test
    void boundary_rain99_99_fallsBackToPreTransferViaHazardWarning() {
        EvaluationResult r = evaluator.evaluate(snap(bd("99.99"), bd("6.000"),
                HazardPointStatus.WARNING, RoadStatus.OPEN, RoadStatus.OPEN));
        assertThat(r.outcome()).isEqualTo(DecisionOutcome.PRE_TRANSFER);
        assertThat(r.reasons().get(0)).contains("二级").contains("WARNING");
    }

    @Test
    void boundary_water5_999_blocksRedButMatchesOrange() {
        EvaluationResult r = evaluator.evaluate(snap(bd("118.00"), bd("5.999"),
                HazardPointStatus.OK, RoadStatus.OPEN, RoadStatus.OPEN));
        assertThat(r.outcome()).isEqualTo(DecisionOutcome.PRE_TRANSFER);
    }

    @Test
    void hazardCriticalAloneTriggersEvacuateNow() {
        EvaluationResult r = evaluator.evaluate(snap(bd("0.00"), bd("0.000"),
                HazardPointStatus.CRITICAL, RoadStatus.OPEN, RoadStatus.OPEN));
        assertThat(r.outcome()).isEqualTo(DecisionOutcome.EVACUATE_NOW);
        assertThat(r.reasons().get(0)).contains("CRITICAL");
    }

    @Test
    void boundary_rainExactly25_isPrepare_24_99_isLowRisk() {
        assertThat(evaluator.evaluate(snap(bd("25.00"), bd("4.999"),
                HazardPointStatus.OK, RoadStatus.OPEN, RoadStatus.OPEN)).outcome())
                .isEqualTo(DecisionOutcome.PREPARE);
        assertThat(evaluator.evaluate(snap(bd("24.99"), bd("4.999"),
                HazardPointStatus.OK, RoadStatus.OPEN, RoadStatus.OPEN)).outcome())
                .isEqualTo(DecisionOutcome.LOW_RISK);
    }

    @Test
    void boundary_waterExactly5_isPrepare_4_99_isLowRisk() {
        assertThat(evaluator.evaluate(snap(bd("0.00"), bd("5.000"),
                HazardPointStatus.OK, RoadStatus.OPEN, RoadStatus.OPEN)).outcome())
                .isEqualTo(DecisionOutcome.PREPARE);
        assertThat(evaluator.evaluate(snap(bd("0.00"), bd("4.999"),
                HazardPointStatus.OK, RoadStatus.OPEN, RoadStatus.OPEN)).outcome())
                .isEqualTo(DecisionOutcome.LOW_RISK);
    }

    @Test
    void insufficientDataIsNotLowRisk() {
        EvaluationResult lowRisk = evaluator.evaluate(snap(bd("0.00"), bd("4.000"),
                HazardPointStatus.OK, RoadStatus.OPEN, RoadStatus.OPEN));
        EvaluationResult noRain = evaluator.evaluate(snap(null, bd("4.000"),
                HazardPointStatus.OK, RoadStatus.OPEN, RoadStatus.OPEN));
        EvaluationResult unknownHazard = evaluator.evaluate(snap(bd("0.00"), bd("4.000"),
                HazardPointStatus.UNKNOWN, RoadStatus.OPEN, RoadStatus.OPEN));
        EvaluationResult unknownPrimaryRoad = evaluator.evaluate(snap(bd("0.00"), bd("4.000"),
                HazardPointStatus.OK, RoadStatus.UNKNOWN, RoadStatus.OPEN));

        assertThat(lowRisk.outcome()).isEqualTo(DecisionOutcome.LOW_RISK);
        assertThat(lowRisk.outcome().isRiskLevel()).isTrue();
        for (EvaluationResult r : new EvaluationResult[]{noRain, unknownHazard, unknownPrimaryRoad}) {
            assertThat(r.outcome()).isEqualTo(DecisionOutcome.INSUFFICIENT_DATA);
            assertThat(r.outcome().isRiskLevel()).isFalse();
            assertThat(r.outcome()).isNotEqualTo(lowRisk.outcome());
            assertThat(r.reasons().get(0)).contains("核心证据缺失").contains("数据不足");
        }
        assertThat(noRain.reasons().get(0)).contains("3小时累计降水");
        assertThat(unknownHazard.reasons().get(0)).contains("隐患点状态");
        assertThat(unknownPrimaryRoad.reasons().get(0)).contains("主路状态");
    }

    @Test
    void secondaryRoadUnknownOnlyAddsNoteAndNeverBlocksLowRisk() {
        EvaluationResult r = evaluator.evaluate(snap(bd("10.00"), bd("4.500"),
                HazardPointStatus.OK, RoadStatus.OPEN, RoadStatus.UNKNOWN));
        assertThat(r.outcome()).isEqualTo(DecisionOutcome.LOW_RISK);
        assertThat(r.reasons()).contains("次路状态未知：需人工核实备用路线");
    }

    @Test
    void staleUpstreamAddsVersionNote() {
        Map<UpstreamKind, UpstreamHealth> health = new EnumMap<>(UpstreamKind.class);
        health.put(UpstreamKind.RAINFALL, new UpstreamHealth(UpstreamStatus.STALE, "rain-old", null));
        EvaluationResult r = evaluator.evaluate(snap(bd("10.00"), bd("4.500"),
                HazardPointStatus.OK, RoadStatus.OPEN, RoadStatus.OPEN, 0, health));
        assertThat(r.outcome()).isEqualTo(DecisionOutcome.LOW_RISK);
        assertThat(r.reasons()).anySatisfy(reason ->
                assertThat(reason).contains("上游降雨返回旧版本数据（version=rain-old）"));
    }
}
