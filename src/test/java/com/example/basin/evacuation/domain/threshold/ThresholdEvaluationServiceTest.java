package com.example.basin.evacuation.domain.threshold;

import com.example.basin.evacuation.domain.decision.Dimension;
import com.example.basin.evacuation.domain.shared.DecisionLevel;
import com.example.basin.evacuation.domain.shared.HazardStatus;
import com.example.basin.evacuation.domain.shared.HealthStatus;
import com.example.basin.evacuation.domain.shared.RoadStatus;
import com.example.basin.evacuation.domain.snapshot.RiskSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ThresholdEvaluationServiceTest {

    private final ThresholdEvaluationService service = new ThresholdEvaluationService();

    private RiskSnapshot.RiskSnapshotBuilder base() {
        return RiskSnapshot.builder()
                .snapshotId("snap-test")
                .districtCode("510182")
                .observedAt(Instant.parse("2026-07-29T03:00:00Z"))
                .rainfall3hMm(BigDecimal.ZERO)
                .waterLevelM(new BigDecimal("4.0"))
                .hazardStatus(HazardStatus.NORMAL)
                .mainRoadStatus(RoadStatus.OPEN)
                .secondaryRoadStatus(RoadStatus.OPEN)
                .vulnerablePopulation(0)
                .rainfallHealth(HealthStatus.HEALTHY)
                .waterLevelHealth(HealthStatus.HEALTHY)
                .hazardHealth(HealthStatus.HEALTHY)
                .roadHealth(HealthStatus.HEALTHY)
                .evidenceVersion("ev-1");
    }

    @Test
    void allClearWithHealthyData_isBlueLowRisk_notInsufficientData() {
        ThresholdResult r = service.evaluate(base().build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.BLUE);
        assertThat(r.breakdown().dataSufficient()).isTrue();
        assertThat(r.computedLevel().isRiskLevel()).isTrue();
    }

    // ---- BLUE boundary ----
    @Test
    void rainfallJustBelowYellow_staysBlue() {
        ThresholdResult r = service.evaluate(
                base().rainfall3hMm(new BigDecimal("49.9")).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.BLUE);
        assertThat(r.breakdown().dimensions().get(Dimension.RAINFALL).severity())
                .isEqualTo(DecisionLevel.BLUE);
    }

    @Test
    void waterJustBelowYellow_staysBlue() {
        ThresholdResult r = service.evaluate(
                base().waterLevelM(new BigDecimal("4.99")).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.BLUE);
    }

    // ---- YELLOW boundary ----
    @Test
    void rainfallAt50_isYellow() {
        ThresholdResult r = service.evaluate(
                base().rainfall3hMm(ThresholdEvaluationService.RAINFALL_YELLOW).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.YELLOW);
    }

    @Test
    void waterAt5_0_isYellow() {
        ThresholdResult r = service.evaluate(
                base().waterLevelM(ThresholdEvaluationService.WATER_YELLOW).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.YELLOW);
    }

    @Test
    void hazardAttention_isYellow() {
        ThresholdResult r = service.evaluate(
                base().hazardStatus(HazardStatus.ATTENTION).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.YELLOW);
    }

    @Test
    void secondaryRoadClosed_isYellow() {
        ThresholdResult r = service.evaluate(
                base().mainRoadStatus(RoadStatus.OPEN)
                        .secondaryRoadStatus(RoadStatus.CLOSED).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.YELLOW);
    }

    @Test
    void oneVulnerablePersonWithHazard_isYellow() {
        ThresholdResult r = service.evaluate(
                base().hazardStatus(HazardStatus.ATTENTION)
                        .vulnerablePopulation(1).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.YELLOW);
    }

    // ---- ORANGE boundary ----
    @Test
    void rainfallAt80_isOrange() {
        ThresholdResult r = service.evaluate(
                base().rainfall3hMm(ThresholdEvaluationService.RAINFALL_ORANGE).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.ORANGE);
    }

    @Test
    void waterAt5_8_isOrange() {
        ThresholdResult r = service.evaluate(
                base().waterLevelM(ThresholdEvaluationService.WATER_ORANGE).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.ORANGE);
    }

    @Test
    void hazardWarning_isOrange() {
        ThresholdResult r = service.evaluate(
                base().hazardStatus(HazardStatus.WARNING).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.ORANGE);
    }

    @Test
    void mainRoadClosed_secondaryUnknown_isOrange() {
        ThresholdResult r = service.evaluate(
                base().mainRoadStatus(RoadStatus.CLOSED)
                        .secondaryRoadStatus(RoadStatus.UNKNOWN).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.ORANGE);
    }

    @Test
    void vulnerableAt101WithHazard_isOrange() {
        ThresholdResult r = service.evaluate(
                base().hazardStatus(HazardStatus.ATTENTION)
                        .vulnerablePopulation(101).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.ORANGE);
    }

    // ---- RED boundary ----
    @Test
    void rainfallAt120_isRed() {
        ThresholdResult r = service.evaluate(
                base().rainfall3hMm(ThresholdEvaluationService.RAINFALL_RED).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.RED);
    }

    @Test
    void waterAt6_5_isRed() {
        ThresholdResult r = service.evaluate(
                base().waterLevelM(ThresholdEvaluationService.WATER_RED).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.RED);
    }

    @Test
    void hazardDanger_isRed() {
        ThresholdResult r = service.evaluate(
                base().hazardStatus(HazardStatus.DANGER).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.RED);
    }

    @Test
    void bothRoadsClosed_isRed() {
        ThresholdResult r = service.evaluate(
                base().mainRoadStatus(RoadStatus.CLOSED)
                        .secondaryRoadStatus(RoadStatus.CLOSED).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.RED);
    }

    @Test
    void vulnerableAt301WithOrangeHazard_isRed() {
        ThresholdResult r = service.evaluate(
                base().hazardStatus(HazardStatus.WARNING)
                        .vulnerablePopulation(301).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.RED);
    }

    @Test
    void vulnerableAt300WithOrangeHazard_staysOrange() {
        ThresholdResult r = service.evaluate(
                base().hazardStatus(HazardStatus.WARNING)
                        .vulnerablePopulation(300).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.ORANGE);
    }

    // ---- Initial snapshot ----
    @Test
    void initialPengzhouSnapshot_isOrange() {
        RiskSnapshot snap = base()
                .snapshotId("sc-510182-20260729T0300")
                .rainfall3hMm(new BigDecimal("118.0"))
                .waterLevelM(new BigDecimal("6.12"))
                .hazardStatus(HazardStatus.WARNING)
                .mainRoadStatus(RoadStatus.CLOSED)
                .secondaryRoadStatus(RoadStatus.UNKNOWN)
                .vulnerablePopulation(286)
                .build();
        ThresholdResult r = service.evaluate(snap);
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.ORANGE);
        assertThat(r.rationale()).contains("阈值评估结果=橙色");
    }

    // ---- Insufficient data vs low risk ----
    @Test
    void bothCoreFeedsDown_isInsufficientData_notBlue() {
        ThresholdResult r = service.evaluate(base()
                .rainfallHealth(HealthStatus.TIMEOUT)
                .rainfall3hMm(null)
                .waterLevelHealth(HealthStatus.UNAVAILABLE)
                .waterLevelM(null)
                .build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.INSUFFICIENT_DATA);
        assertThat(r.breakdown().dataSufficient()).isFalse();
        assertThat(r.breakdown().missingDimensions()).hasSize(2);
    }

    @Test
    void threeFeedsDown_isInsufficientData() {
        ThresholdResult r = service.evaluate(base()
                .rainfallHealth(HealthStatus.HEALTHY)
                .waterLevelHealth(HealthStatus.TIMEOUT).waterLevelM(null)
                .hazardHealth(HealthStatus.UNAVAILABLE).hazardStatus(null)
                .roadHealth(HealthStatus.TIMEOUT).mainRoadStatus(null)
                .build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.INSUFFICIENT_DATA);
    }

    @Test
    void twoNonCoreTimeouts_oneStale_oneHealthy_isStillSufficient() {
        ThresholdResult r = service.evaluate(base()
                .rainfallHealth(HealthStatus.STALE).rainfall3hMm(new BigDecimal("118.0"))
                .waterLevelHealth(HealthStatus.HEALTHY).waterLevelM(new BigDecimal("6.12"))
                .hazardHealth(HealthStatus.TIMEOUT).hazardStatus(null)
                .roadHealth(HealthStatus.TIMEOUT).mainRoadStatus(null)
                .build());
        assertThat(r.breakdown().dataSufficient()).isTrue();
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.ORANGE);
        assertThat(r.breakdown().staleDimensions()).extracting(Enum::name)
                .contains("RAINFALL");
    }

    @Test
    void staleFeedIsUsableAndRecorded() {
        ThresholdResult r = service.evaluate(base()
                .rainfallHealth(HealthStatus.STALE)
                .rainfall3hMm(new BigDecimal("90"))
                .build());
        assertThat(r.breakdown().dataSufficient()).isTrue();
        assertThat(r.breakdown().staleDimensions()).isNotEmpty();
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.ORANGE);
    }

    @Test
    void exposureAloneWithoutHazard_doesNotEscalate() {
        ThresholdResult r = service.evaluate(base()
                .vulnerablePopulation(500).build());
        assertThat(r.computedLevel()).isEqualTo(DecisionLevel.BLUE);
    }
}
