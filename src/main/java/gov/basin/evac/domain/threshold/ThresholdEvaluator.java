package gov.basin.evac.domain.threshold;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import gov.basin.evac.config.DecisionProperties;
import gov.basin.evac.config.DecisionProperties.Ladder;
import gov.basin.evac.domain.entity.RiskSnapshot;
import gov.basin.evac.domain.entity.SnapshotUpstreamHealth;
import gov.basin.evac.domain.model.DecisionLevel;
import gov.basin.evac.domain.model.HazardPointStatus;
import gov.basin.evac.domain.model.RoadStatus;
import gov.basin.evac.domain.model.UpstreamSource;
import gov.basin.evac.domain.model.UpstreamStatus;

/**
 * Independent threshold-evaluation module. It is the SOLE owner of the decision rules; nothing
 * else in the system (least of all controllers) encodes how evidence maps to a decision level.
 *
 * <p>Key rule: {@link DecisionLevel#INSUFFICIENT_DATA} is a completely separate outcome from
 * {@link DecisionLevel#LOW}. If any required quantitative feed (rainfall, river level) is
 * unhealthy (TIMEOUT / STALE / ERROR), missing, or its evidence is older than the configured
 * freshness window, the module returns INSUFFICIENT_DATA — it never silently downgrades missing
 * evidence to "low risk". LOW is only ever returned when the evidence is complete AND benign.
 */
@Component
public class ThresholdEvaluator {

    /** Feeds whose numeric readings are indispensable for any risk judgement. */
    private static final Set<UpstreamSource> REQUIRED_SOURCES =
            EnumSet.of(UpstreamSource.RAINFALL, UpstreamSource.RIVER_LEVEL);

    private final DecisionProperties properties;

    public ThresholdEvaluator(DecisionProperties properties) {
        this.properties = properties;
    }

    /**
     * Evaluate a snapshot as of {@code now}. {@code now} matters because evidence freshness is
     * measured against it, and it is supplied by an injectable clock upstream.
     */
    public DecisionEvaluation evaluate(RiskSnapshot snapshot, Instant now) {
        List<String> dataGaps = collectDataGaps(snapshot, now);
        if (!dataGaps.isEmpty()) {
            return new DecisionEvaluation(
                    DecisionLevel.INSUFFICIENT_DATA,
                    "数据不足，无法评估风险：" + String.join("；", dataGaps)
                            + "。（注意：这与低风险不同，需人工核实上游数据。）",
                    snapshot.getVulnerablePopulationPersons());
        }

        // Evidence is complete and fresh: rank each signal, take the most severe.
        DecisionLevel rainLevel = ladderLevel(snapshot.getRainfall3hMm(),
                properties.getThresholds().getRainfall3hMm());
        DecisionLevel riverLevel = ladderLevel(snapshot.getRiverLevelM(),
                properties.getThresholds().getRiverLevelM());
        DecisionLevel hazardLevel = hazardLevel(snapshot.getHazardPointStatus());
        DecisionLevel roadLevel = roadLevel(snapshot.getPrimaryRoadStatus(),
                snapshot.getSecondaryRoadStatus());

        DecisionLevel worst = mostSevere(rainLevel, riverLevel, hazardLevel, roadLevel);

        String rationale = String.format(
                "3小时降水%.2f毫米→%s；河道水位%.2f米→%s；隐患点%s→%s；道路(主:%s/次:%s)→%s。综合取最严重等级：%s。",
                snapshot.getRainfall3hMm(), rainLevel,
                snapshot.getRiverLevelM(), riverLevel,
                snapshot.getHazardPointStatus(), hazardLevel,
                snapshot.getPrimaryRoadStatus(), snapshot.getSecondaryRoadStatus(), roadLevel,
                worst);

        return new DecisionEvaluation(worst, rationale, snapshot.getVulnerablePopulationPersons());
    }

    private List<String> collectDataGaps(RiskSnapshot snapshot, Instant now) {
        List<String> gaps = new ArrayList<>();
        Duration maxAge = Duration.ofMinutes(properties.getUpstream().getMaxEvidenceAgeMinutes());

        for (UpstreamSource required : REQUIRED_SOURCES) {
            SnapshotUpstreamHealth health = snapshot.getUpstreamHealth().stream()
                    .filter(h -> h.getSource() == required)
                    .findFirst()
                    .orElse(null);
            if (health == null) {
                gaps.add(required + "无健康记录");
                continue;
            }
            if (health.getStatus() != UpstreamStatus.OK) {
                gaps.add(required + "状态为" + health.getStatus());
                continue;
            }
            Instant observedAt = health.getObservedAt();
            if (observedAt == null) {
                gaps.add(required + "缺少观测时间");
            } else if (Duration.between(observedAt, now).compareTo(maxAge) > 0) {
                gaps.add(required + "证据过期(>" + maxAge.toMinutes() + "分钟)");
            }
        }
        return gaps;
    }

    private DecisionLevel ladderLevel(BigDecimal reading, Ladder ladder) {
        double value = reading.doubleValue();
        if (value >= ladder.getMoveNow()) {
            return DecisionLevel.MOVE_NOW;
        }
        if (value >= ladder.getMovePrepare()) {
            return DecisionLevel.MOVE_PREPARE;
        }
        if (value >= ladder.getWatch()) {
            return DecisionLevel.WATCH;
        }
        return DecisionLevel.LOW;
    }

    private DecisionLevel hazardLevel(HazardPointStatus status) {
        return switch (status) {
            case DANGER -> DecisionLevel.MOVE_NOW;
            case WARNING -> DecisionLevel.MOVE_PREPARE;
            case NORMAL -> DecisionLevel.LOW;
            // A single qualitative unknown does not by itself void the (present) quantitative
            // evidence; it nudges the level up to WATCH so operators verify it.
            case UNKNOWN -> DecisionLevel.WATCH;
        };
    }

    private DecisionLevel roadLevel(RoadStatus primary, RoadStatus secondary) {
        // Both escape routes closed => evacuation window is closing => MOVE_NOW.
        if (primary == RoadStatus.CLOSED && secondary == RoadStatus.CLOSED) {
            return DecisionLevel.MOVE_NOW;
        }
        // Primary route closed while secondary is uncertain/open => prepare to move.
        if (primary == RoadStatus.CLOSED) {
            return DecisionLevel.MOVE_PREPARE;
        }
        if (primary == RoadStatus.UNKNOWN || secondary == RoadStatus.CLOSED) {
            return DecisionLevel.WATCH;
        }
        return DecisionLevel.LOW;
    }

    private DecisionLevel mostSevere(DecisionLevel... levels) {
        DecisionLevel worst = DecisionLevel.LOW;
        for (DecisionLevel level : levels) {
            if (level.priority() > worst.priority()) {
                worst = level;
            }
        }
        return worst;
    }
}
