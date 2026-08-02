package cn.gov.basin.evacuation.domain.threshold;

import cn.gov.basin.evacuation.domain.decision.DecisionLevel;
import cn.gov.basin.evacuation.domain.snapshot.HazardStatus;
import cn.gov.basin.evacuation.domain.snapshot.RiskSnapshotView;
import cn.gov.basin.evacuation.domain.snapshot.RoadStatus;
import cn.gov.basin.evacuation.domain.snapshot.UpstreamHealth;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static cn.gov.basin.evacuation.domain.threshold.ThresholdConfig.RAIN_L2;
import static cn.gov.basin.evacuation.domain.threshold.ThresholdConfig.RAIN_L3;
import static cn.gov.basin.evacuation.domain.threshold.ThresholdConfig.RAIN_L4;
import static cn.gov.basin.evacuation.domain.threshold.ThresholdConfig.WATER_L2;
import static cn.gov.basin.evacuation.domain.threshold.ThresholdConfig.WATER_L3;
import static cn.gov.basin.evacuation.domain.threshold.ThresholdConfig.WATER_L4;

@Service
public class ThresholdEvaluationService {

    private static final Set<UpstreamHealth> CORE_UNAVAILABLE =
            EnumSet.of(UpstreamHealth.TIMEOUT, UpstreamHealth.ERROR);

    public EvaluationResult evaluate(RiskSnapshotView snapshot) {
        List<String> missing = new ArrayList<>();
        List<String> rules = new ArrayList<>();

        EvidenceRef evidence = new EvidenceRef(
                snapshot.getSnapshotId(),
                snapshot.getRainfallVersion(),
                snapshot.getRainfallHealth(),
                snapshot.getWaterLevelVersion(),
                snapshot.getWaterLevelHealth(),
                snapshot.getHazardVersion(),
                snapshot.getHazardHealth(),
                snapshot.getInfrastructureVersion(),
                snapshot.getInfrastructureHealth()
        );

        boolean coreAvailable = checkCoreAvailability(snapshot, missing);
        if (!coreAvailable) {
            return new EvaluationResult(
                    DecisionLevel.INSUFFICIENT_DATA,
                    "核心上游数据缺失或超时，无法给出可靠转移等级，请人工核实后再下达指令",
                    List.of(),
                    List.copyOf(missing),
                    evidence
            );
        }

        int score = 1;

        if (snapshot.getRainfall3hMm() != null) {
            BigDecimal rain = snapshot.getRainfall3hMm();
            if (rain.compareTo(RAIN_L4) >= 0) {
                score = Math.max(score, 4);
                rules.add("RAIN_3H_GE_100");
            } else if (rain.compareTo(RAIN_L3) >= 0) {
                score = Math.max(score, 3);
                rules.add("RAIN_3H_GE_80");
            } else if (rain.compareTo(RAIN_L2) >= 0) {
                score = Math.max(score, 2);
                rules.add("RAIN_3H_GE_50");
            }
        }

        if (snapshot.getWaterLevelM() != null) {
            BigDecimal water = snapshot.getWaterLevelM();
            if (water.compareTo(WATER_L4) >= 0) {
                score = Math.max(score, 4);
                rules.add("WATER_LEVEL_GE_6_00");
            } else if (water.compareTo(WATER_L3) >= 0) {
                score = Math.max(score, 3);
                rules.add("WATER_LEVEL_GE_5_50");
            } else if (water.compareTo(WATER_L2) >= 0) {
                score = Math.max(score, 2);
                rules.add("WATER_LEVEL_GE_5_00");
            }
        }

        HazardStatus hazard = snapshot.getHazardStatus();
        if (hazard == HazardStatus.CRITICAL) {
            score = Math.max(score, 4);
            rules.add("HAZARD_CRITICAL");
        } else if (hazard == HazardStatus.WARNING) {
            score = Math.max(score, 3);
            rules.add("HAZARD_WARNING");
        } else if (hazard == HazardStatus.WATCH) {
            score = Math.max(score, 2);
            rules.add("HAZARD_WATCH");
        }

        RoadStatus primary = snapshot.getPrimaryRoadStatus();
        if (primary == RoadStatus.CLOSED) {
            score = Math.max(score, 2);
            rules.add("PRIMARY_ROAD_CLOSED");
        }

        if (snapshot.getHazardHealth() != UpstreamHealth.OK
                && snapshot.getHazardStatus() == null) {
            missing.add("HAZARD_DATA_UNAVAILABLE");
        }
        if (snapshot.getInfrastructureHealth() != UpstreamHealth.OK
                && (primary == null || primary == RoadStatus.UNKNOWN)) {
            missing.add("INFRASTRUCTURE_DATA_DEGRADED");
        }

        DecisionLevel level = switch (score) {
            case 4 -> DecisionLevel.LEVEL_4;
            case 3 -> DecisionLevel.LEVEL_3;
            case 2 -> DecisionLevel.LEVEL_2;
            default -> DecisionLevel.LEVEL_1;
        };

        String message = buildMessage(level, snapshot.getVulnerablePopulation(), missing);
        return new EvaluationResult(level, message, List.copyOf(rules),
                List.copyOf(missing), evidence);
    }

    private boolean checkCoreAvailability(RiskSnapshotView s, List<String> missing) {
        boolean available = true;
        if (CORE_UNAVAILABLE.contains(s.getRainfallHealth()) || s.getRainfall3hMm() == null) {
            missing.add("RAINFALL_UNAVAILABLE");
            available = false;
        }
        if (CORE_UNAVAILABLE.contains(s.getWaterLevelHealth()) || s.getWaterLevelM() == null) {
            missing.add("WATER_LEVEL_UNAVAILABLE");
            available = false;
        }
        if (CORE_UNAVAILABLE.contains(s.getHazardHealth())) {
            missing.add("HAZARD_UNAVAILABLE");
        }
        if (CORE_UNAVAILABLE.contains(s.getInfrastructureHealth())) {
            missing.add("INFRASTRUCTURE_UNAVAILABLE");
        }
        if (s.getHazardHealth() == UpstreamHealth.STALE && s.getHazardStatus() == null) {
            missing.add("HAZARD_STALE");
        }
        return available;
    }

    private String buildMessage(DecisionLevel level, Integer vulnerable, List<String> missing) {
        String pop = vulnerable == null ? "未知" : (vulnerable + " 人");
        String suffix = missing.isEmpty() ? "" : "；受影响证据: " + String.join(",", missing);
        return switch (level) {
            case LEVEL_4 -> "立即组织转移（一级响应），脆弱人群 " + pop + " 人优先撤离" + suffix;
            case LEVEL_3 -> "做好转移准备（二级响应），通知脆弱人群 " + pop + " 人待命" + suffix;
            case LEVEL_2 -> "加强监测（三级响应），关注雨情水情和隐患点变化" + suffix;
            case LEVEL_1 -> "风险较低（四级响应），保持常规值班，脆弱人群基数 " + pop + suffix;
            case INSUFFICIENT_DATA -> throw new IllegalStateException("handled elsewhere");
        };
    }
}
