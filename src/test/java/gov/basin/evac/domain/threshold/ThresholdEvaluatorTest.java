package gov.basin.evac.domain.threshold;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import gov.basin.evac.config.DecisionProperties;
import gov.basin.evac.domain.entity.RiskSnapshot;
import gov.basin.evac.domain.entity.SnapshotUpstreamHealth;
import gov.basin.evac.domain.model.DecisionLevel;
import gov.basin.evac.domain.model.HazardPointStatus;
import gov.basin.evac.domain.model.RoadStatus;
import gov.basin.evac.domain.model.UpstreamSource;
import gov.basin.evac.domain.model.UpstreamStatus;

/**
 * Pure unit tests for the threshold module, covering every decision level plus at least one
 * boundary condition per level and the critical INSUFFICIENT_DATA-vs-LOW distinction.
 */
class ThresholdEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-07-29T03:10:00Z");

    private final ThresholdEvaluator evaluator = new ThresholdEvaluator(defaultProperties());

    private static DecisionProperties defaultProperties() {
        DecisionProperties p = new DecisionProperties();
        p.getThresholds().getRainfall3hMm().setWatch(50);
        p.getThresholds().getRainfall3hMm().setMovePrepare(80);
        p.getThresholds().getRainfall3hMm().setMoveNow(100);
        p.getThresholds().getRiverLevelM().setWatch(4.0);
        p.getThresholds().getRiverLevelM().setMovePrepare(5.5);
        p.getThresholds().getRiverLevelM().setMoveNow(6.0);
        p.getUpstream().setMaxEvidenceAgeMinutes(30);
        return p;
    }

    /** Build a snapshot with all four feeds healthy and freshly observed at NOW. */
    private RiskSnapshot healthy(BigDecimal rain, BigDecimal river,
                                 HazardPointStatus hazard, RoadStatus primary, RoadStatus secondary) {
        RiskSnapshot s = new RiskSnapshot("s1", "510182", NOW, rain, river,
                hazard, primary, secondary, 286);
        for (UpstreamSource src : UpstreamSource.values()) {
            s.addUpstreamHealth(new SnapshotUpstreamHealth(src, UpstreamStatus.OK, "v1", NOW));
        }
        return s;
    }

    @Test
    void lowWhenEvidenceCompleteAndBenign() {
        RiskSnapshot s = healthy(BigDecimal.valueOf(10), BigDecimal.valueOf(2.0),
                HazardPointStatus.NORMAL, RoadStatus.OPEN, RoadStatus.OPEN);
        assertThat(evaluator.evaluate(s, NOW).level()).isEqualTo(DecisionLevel.LOW);
    }

    @Test
    void watchAtRainfallLowerBoundary() {
        // Boundary: rainfall exactly at watch threshold (50) => WATCH, not LOW.
        RiskSnapshot s = healthy(BigDecimal.valueOf(50), BigDecimal.valueOf(2.0),
                HazardPointStatus.NORMAL, RoadStatus.OPEN, RoadStatus.OPEN);
        assertThat(evaluator.evaluate(s, NOW).level()).isEqualTo(DecisionLevel.WATCH);
    }

    @Test
    void watchJustBelowRainfallBoundaryStaysLow() {
        // Boundary companion: 49.99 stays LOW.
        RiskSnapshot s = healthy(BigDecimal.valueOf(49.99), BigDecimal.valueOf(2.0),
                HazardPointStatus.NORMAL, RoadStatus.OPEN, RoadStatus.OPEN);
        assertThat(evaluator.evaluate(s, NOW).level()).isEqualTo(DecisionLevel.LOW);
    }

    @Test
    void movePrepareAtRiverLevelBoundary() {
        // Boundary: river level exactly at move-prepare threshold (5.5) => MOVE_PREPARE.
        RiskSnapshot s = healthy(BigDecimal.valueOf(10), BigDecimal.valueOf(5.5),
                HazardPointStatus.NORMAL, RoadStatus.OPEN, RoadStatus.OPEN);
        assertThat(evaluator.evaluate(s, NOW).level()).isEqualTo(DecisionLevel.MOVE_PREPARE);
    }

    @Test
    void moveNowAtRiverLevelBoundary() {
        // Boundary: river level exactly at move-now threshold (6.0) => MOVE_NOW.
        RiskSnapshot s = healthy(BigDecimal.valueOf(10), BigDecimal.valueOf(6.0),
                HazardPointStatus.NORMAL, RoadStatus.OPEN, RoadStatus.OPEN);
        assertThat(evaluator.evaluate(s, NOW).level()).isEqualTo(DecisionLevel.MOVE_NOW);
    }

    @Test
    void takesMostSevereAcrossSignals() {
        // Benign rain/river but both roads closed => MOVE_NOW dominates.
        RiskSnapshot s = healthy(BigDecimal.valueOf(5), BigDecimal.valueOf(1.0),
                HazardPointStatus.NORMAL, RoadStatus.CLOSED, RoadStatus.CLOSED);
        assertThat(evaluator.evaluate(s, NOW).level()).isEqualTo(DecisionLevel.MOVE_NOW);
    }

    @Test
    void insufficientDataWhenRequiredFeedTimesOut() {
        RiskSnapshot s = new RiskSnapshot("s1", "510182", NOW, BigDecimal.valueOf(5),
                BigDecimal.valueOf(1.0), HazardPointStatus.NORMAL, RoadStatus.OPEN, RoadStatus.OPEN, 286);
        s.addUpstreamHealth(new SnapshotUpstreamHealth(UpstreamSource.RAINFALL, UpstreamStatus.TIMEOUT, null, NOW));
        s.addUpstreamHealth(new SnapshotUpstreamHealth(UpstreamSource.RIVER_LEVEL, UpstreamStatus.OK, "v1", NOW));
        s.addUpstreamHealth(new SnapshotUpstreamHealth(UpstreamSource.HAZARD, UpstreamStatus.OK, "v1", NOW));
        s.addUpstreamHealth(new SnapshotUpstreamHealth(UpstreamSource.ROAD, UpstreamStatus.OK, "v1", NOW));
        DecisionEvaluation result = evaluator.evaluate(s, NOW);
        assertThat(result.level()).isEqualTo(DecisionLevel.INSUFFICIENT_DATA);
        // Critically: this is NOT LOW even though the readings themselves are benign.
        assertThat(result.level()).isNotEqualTo(DecisionLevel.LOW);
    }

    @Test
    void insufficientDataWhenRequiredFeedStale() {
        RiskSnapshot s = new RiskSnapshot("s1", "510182", NOW, BigDecimal.valueOf(5),
                BigDecimal.valueOf(1.0), HazardPointStatus.NORMAL, RoadStatus.OPEN, RoadStatus.OPEN, 286);
        s.addUpstreamHealth(new SnapshotUpstreamHealth(UpstreamSource.RAINFALL, UpstreamStatus.STALE, "old", NOW));
        s.addUpstreamHealth(new SnapshotUpstreamHealth(UpstreamSource.RIVER_LEVEL, UpstreamStatus.OK, "v1", NOW));
        s.addUpstreamHealth(new SnapshotUpstreamHealth(UpstreamSource.HAZARD, UpstreamStatus.OK, "v1", NOW));
        s.addUpstreamHealth(new SnapshotUpstreamHealth(UpstreamSource.ROAD, UpstreamStatus.OK, "v1", NOW));
        assertThat(evaluator.evaluate(s, NOW).level()).isEqualTo(DecisionLevel.INSUFFICIENT_DATA);
    }

    @Test
    void insufficientDataBoundaryAtEvidenceAgeExpiry() {
        // Boundary: evidence observed exactly maxAge+1s before now => stale => INSUFFICIENT_DATA.
        Instant tooOld = NOW.minusSeconds(30 * 60 + 1);
        RiskSnapshot s = new RiskSnapshot("s1", "510182", tooOld, BigDecimal.valueOf(5),
                BigDecimal.valueOf(1.0), HazardPointStatus.NORMAL, RoadStatus.OPEN, RoadStatus.OPEN, 286);
        s.addUpstreamHealth(new SnapshotUpstreamHealth(UpstreamSource.RAINFALL, UpstreamStatus.OK, "v1", tooOld));
        s.addUpstreamHealth(new SnapshotUpstreamHealth(UpstreamSource.RIVER_LEVEL, UpstreamStatus.OK, "v1", tooOld));
        s.addUpstreamHealth(new SnapshotUpstreamHealth(UpstreamSource.HAZARD, UpstreamStatus.OK, "v1", tooOld));
        s.addUpstreamHealth(new SnapshotUpstreamHealth(UpstreamSource.ROAD, UpstreamStatus.OK, "v1", tooOld));
        assertThat(evaluator.evaluate(s, NOW).level()).isEqualTo(DecisionLevel.INSUFFICIENT_DATA);

        // And exactly at the boundary (age == maxAge) it is still fresh => a risk level, not gap.
        Instant justFresh = NOW.minusSeconds(30 * 60);
        RiskSnapshot fresh = new RiskSnapshot("s2", "510182", justFresh, BigDecimal.valueOf(5),
                BigDecimal.valueOf(1.0), HazardPointStatus.NORMAL, RoadStatus.OPEN, RoadStatus.OPEN, 286);
        for (UpstreamSource src : UpstreamSource.values()) {
            fresh.addUpstreamHealth(new SnapshotUpstreamHealth(src, UpstreamStatus.OK, "v1", justFresh));
        }
        assertThat(evaluator.evaluate(fresh, NOW).level()).isEqualTo(DecisionLevel.LOW);
    }

    @Test
    void seedSnapshotEvaluatesToMoveNow() {
        // The brief's initial snapshot: rain 118, river 6.12, hazard warning, primary closed.
        RiskSnapshot s = healthy(new BigDecimal("118.00"), new BigDecimal("6.12"),
                HazardPointStatus.WARNING, RoadStatus.CLOSED, RoadStatus.UNKNOWN);
        assertThat(evaluator.evaluate(s, NOW).level()).isEqualTo(DecisionLevel.MOVE_NOW);
    }
}
