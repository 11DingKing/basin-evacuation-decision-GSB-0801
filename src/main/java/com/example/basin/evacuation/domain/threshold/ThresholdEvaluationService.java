package com.example.basin.evacuation.domain.threshold;

import com.example.basin.evacuation.domain.decision.Dimension;
import com.example.basin.evacuation.domain.decision.DimensionBreakdown;
import com.example.basin.evacuation.domain.shared.DecisionLevel;
import com.example.basin.evacuation.domain.shared.HazardStatus;
import com.example.basin.evacuation.domain.shared.HealthStatus;
import com.example.basin.evacuation.domain.shared.RoadStatus;
import com.example.basin.evacuation.domain.shared.UpstreamType;
import com.example.basin.evacuation.domain.snapshot.RiskSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Independent domain module that evaluates an immutable {@link RiskSnapshot}
 * against watershed thresholds. It produces a computed {@link DecisionLevel}
 * together with a per-dimension evidence breakdown.
 *
 * <p>This class is the ONLY place where threshold rules live. Controllers and
 * application services must not encode decision rules themselves.
 *
 * <p>"Insufficient data" is a distinct outcome from "low risk": when the
 * required upstream evidence is missing, the result is {@link DecisionLevel#INSUFFICIENT_DATA}
 * rather than a BLUE assessment.
 */
@Service
public class ThresholdEvaluationService {

    static final BigDecimal RAINFALL_YELLOW = new BigDecimal("50");
    static final BigDecimal RAINFALL_ORANGE = new BigDecimal("80");
    static final BigDecimal RAINFALL_RED = new BigDecimal("120");

    static final BigDecimal WATER_YELLOW = new BigDecimal("5.0");
    static final BigDecimal WATER_ORANGE = new BigDecimal("5.8");
    static final BigDecimal WATER_RED = new BigDecimal("6.5");

    static final long POP_YELLOW = 1L;
    static final long POP_ORANGE = 101L;
    static final long POP_RED = 301L;

    public ThresholdResult evaluate(RiskSnapshot snapshot) {
        List<UpstreamType> missing = new ArrayList<>();
        List<UpstreamType> stale = new ArrayList<>();
        Map<Dimension, DimensionBreakdown.DimensionAssessment> dims = new EnumMap<>(Dimension.class);

        boolean rainfallDown = isDown(snapshot.getRainfallHealth());
        boolean waterDown = isDown(snapshot.getWaterLevelHealth());
        boolean hazardDown = isDown(snapshot.getHazardHealth());
        boolean roadDown = isDown(snapshot.getRoadHealth());

        if (rainfallDown) missing.add(UpstreamType.RAINFALL);
        else if (snapshot.getRainfallHealth() == HealthStatus.STALE) stale.add(UpstreamType.RAINFALL);
        if (waterDown) missing.add(UpstreamType.WATER_LEVEL);
        else if (snapshot.getWaterLevelHealth() == HealthStatus.STALE) stale.add(UpstreamType.WATER_LEVEL);
        if (hazardDown) missing.add(UpstreamType.HAZARD);
        else if (snapshot.getHazardHealth() == HealthStatus.STALE) stale.add(UpstreamType.HAZARD);
        if (roadDown) missing.add(UpstreamType.ROAD);
        else if (snapshot.getRoadHealth() == HealthStatus.STALE) stale.add(UpstreamType.ROAD);

        int downCount = (rainfallDown ? 1 : 0) + (waterDown ? 1 : 0)
                + (hazardDown ? 1 : 0) + (roadDown ? 1 : 0);
        boolean coreBothDown = rainfallDown && waterDown;
        boolean sufficient = !coreBothDown && downCount < 3;

        if (!sufficient) {
            return new ThresholdResult(
                    DecisionLevel.INSUFFICIENT_DATA,
                    DimensionBreakdown.builder()
                            .dataSufficient(false)
                            .missingDimensions(List.copyOf(missing))
                            .staleDimensions(List.copyOf(stale))
                            .dimensions(Map.copyOf(dims))
                            .build(),
                    "数据不足：降雨与水位两路核心上游均不可用，或四类上游中至少三类超时/不可用，"
                            + "无法形成可靠转移建议。缺失上游=" + missing + "，陈旧上游=" + stale);
        }

        DecisionLevel rainfall = evalRainfall(snapshot, rainfallDown, dims);
        DecisionLevel water = evalWater(snapshot, waterDown, dims);
        DecisionLevel hazard = evalHazard(snapshot, hazardDown, dims);

        DecisionLevel hazardMax = maxRisk(rainfall, water, hazard);
        DecisionLevel exposure = evalExposure(snapshot.getVulnerablePopulation(), hazardMax, dims);
        DecisionLevel road = evalRoad(snapshot, roadDown, dims);

        DecisionLevel overall = maxRisk(hazardMax, exposure, road);

        String rationale = buildRationale(overall, dims, stale, missing);
        return new ThresholdResult(
                overall,
                DimensionBreakdown.builder()
                        .dataSufficient(true)
                        .missingDimensions(List.copyOf(missing))
                        .staleDimensions(List.copyOf(stale))
                        .dimensions(Map.copyOf(dims))
                        .build(),
                rationale);
    }

    private DecisionLevel evalRainfall(RiskSnapshot s, boolean down,
                                       Map<Dimension, DimensionBreakdown.DimensionAssessment> dims) {
        if (down || s.getRainfall3hMm() == null) {
            return null;
        }
        BigDecimal v = s.getRainfall3hMm();
        DecisionLevel level;
        String detail;
        if (v.compareTo(RAINFALL_RED) >= 0) {
            level = DecisionLevel.RED;
            detail = "3小时累计降水 " + v + "mm ≥ " + RAINFALL_RED + "mm";
        } else if (v.compareTo(RAINFALL_ORANGE) >= 0) {
            level = DecisionLevel.ORANGE;
            detail = "3小时累计降水 " + v + "mm ≥ " + RAINFALL_ORANGE + "mm";
        } else if (v.compareTo(RAINFALL_YELLOW) >= 0) {
            level = DecisionLevel.YELLOW;
            detail = "3小时累计降水 " + v + "mm ≥ " + RAINFALL_YELLOW + "mm";
        } else {
            level = DecisionLevel.BLUE;
            detail = "3小时累计降水 " + v + "mm < " + RAINFALL_YELLOW + "mm";
        }
        dims.put(Dimension.RAINFALL, DimensionBreakdown.DimensionAssessment.builder()
                .severity(level).healthy(s.getRainfallHealth() == HealthStatus.HEALTHY)
                .usable(true).value(v.toPlainString() + "mm").detail(detail).build());
        return level;
    }

    private DecisionLevel evalWater(RiskSnapshot s, boolean down,
                                    Map<Dimension, DimensionBreakdown.DimensionAssessment> dims) {
        if (down || s.getWaterLevelM() == null) {
            return null;
        }
        BigDecimal v = s.getWaterLevelM();
        DecisionLevel level;
        String detail;
        if (v.compareTo(WATER_RED) >= 0) {
            level = DecisionLevel.RED;
            detail = "河道水位 " + v + "m ≥ " + WATER_RED + "m";
        } else if (v.compareTo(WATER_ORANGE) >= 0) {
            level = DecisionLevel.ORANGE;
            detail = "河道水位 " + v + "m ≥ " + WATER_ORANGE + "m";
        } else if (v.compareTo(WATER_YELLOW) >= 0) {
            level = DecisionLevel.YELLOW;
            detail = "河道水位 " + v + "m ≥ " + WATER_YELLOW + "m";
        } else {
            level = DecisionLevel.BLUE;
            detail = "河道水位 " + v + "m < " + WATER_YELLOW + "m";
        }
        dims.put(Dimension.WATER_LEVEL, DimensionBreakdown.DimensionAssessment.builder()
                .severity(level).healthy(s.getWaterLevelHealth() == HealthStatus.HEALTHY)
                .usable(true).value(v.toPlainString() + "m").detail(detail).build());
        return level;
    }

    private DecisionLevel evalHazard(RiskSnapshot s, boolean down,
                                     Map<Dimension, DimensionBreakdown.DimensionAssessment> dims) {
        if (down || s.getHazardStatus() == null) {
            return null;
        }
        HazardStatus h = s.getHazardStatus();
        DecisionLevel level = switch (h) {
            case NORMAL -> DecisionLevel.BLUE;
            case ATTENTION -> DecisionLevel.YELLOW;
            case WARNING -> DecisionLevel.ORANGE;
            case DANGER -> DecisionLevel.RED;
        };
        String detail = "隐患点状态 " + h;
        dims.put(Dimension.HAZARD, DimensionBreakdown.DimensionAssessment.builder()
                .severity(level).healthy(s.getHazardHealth() == HealthStatus.HEALTHY)
                .usable(true).value(h.name()).detail(detail).build());
        return level;
    }

    private DecisionLevel evalRoad(RiskSnapshot s, boolean down,
                                   Map<Dimension, DimensionBreakdown.DimensionAssessment> dims) {
        if (down || s.getMainRoadStatus() == null) {
            return null;
        }
        RoadStatus main = s.getMainRoadStatus();
        RoadStatus sec = s.getSecondaryRoadStatus();
        DecisionLevel level;
        String detail;
        if (main == RoadStatus.CLOSED && sec == RoadStatus.CLOSED) {
            level = DecisionLevel.RED;
            detail = "主路与次路均封闭，转移通道完全中断";
        } else if (main == RoadStatus.CLOSED) {
            level = DecisionLevel.ORANGE;
            detail = "主路封闭（次路=" + sec + "），主要转移通道受阻";
        } else if (sec == RoadStatus.CLOSED) {
            level = DecisionLevel.YELLOW;
            detail = "次路封闭（主路=" + main + "），备用通道受限";
        } else {
            level = DecisionLevel.BLUE;
            detail = "主路=" + main + "，次路=" + sec + "，通道基本畅通";
        }
        dims.put(Dimension.ROAD, DimensionBreakdown.DimensionAssessment.builder()
                .severity(level).healthy(s.getRoadHealth() == HealthStatus.HEALTHY)
                .usable(true).value("main=" + main + ",secondary=" + sec).detail(detail).build());
        return level;
    }

    private DecisionLevel evalExposure(long population, DecisionLevel hazardMax,
                                       Map<Dimension, DimensionBreakdown.DimensionAssessment> dims) {
        DecisionLevel level;
        String detail;
        if (hazardMax == null || hazardMax == DecisionLevel.BLUE) {
            level = DecisionLevel.BLUE;
            detail = "脆弱人群 " + population + " 人，但当前无显著致灾因子，不单独触发升级";
        } else if (population >= POP_RED && hazardMax.rank() >= DecisionLevel.ORANGE.rank()) {
            level = DecisionLevel.RED;
            detail = "脆弱人群 " + population + " 人 ≥ " + POP_RED + " 且存在橙色及以上致灾因子";
        } else if (population >= POP_RED) {
            level = DecisionLevel.ORANGE;
            detail = "脆弱人群 " + population + " 人 ≥ " + POP_RED;
        } else if (population >= POP_ORANGE) {
            level = DecisionLevel.ORANGE;
            detail = "脆弱人群 " + population + " 人 ≥ " + POP_ORANGE;
        } else if (population >= POP_YELLOW) {
            level = DecisionLevel.YELLOW;
            detail = "脆弱人群 " + population + " 人 > 0";
        } else {
            level = DecisionLevel.BLUE;
            detail = "脆弱人群为 0 人";
        }
        dims.put(Dimension.EXPOSURE, DimensionBreakdown.DimensionAssessment.builder()
                .severity(level).healthy(true).usable(true)
                .value(population + "人").detail(detail).build());
        return level;
    }

    private boolean isDown(HealthStatus health) {
        return health != null && health.isDown();
    }

    private DecisionLevel maxRisk(DecisionLevel... levels) {
        DecisionLevel max = null;
        for (DecisionLevel l : levels) {
            if (l == null || !l.isRiskLevel()) continue;
            if (max == null || l.rank() > max.rank()) max = l;
        }
        return max;
    }

    private String buildRationale(DecisionLevel level,
                                  Map<Dimension, DimensionBreakdown.DimensionAssessment> dims,
                                  List<UpstreamType> stale,
                                  List<UpstreamType> missing) {
        StringBuilder sb = new StringBuilder("阈值评估结果=").append(level.label());
        dims.forEach((k, v) -> {
            if (v.severity() == level) {
                sb.append("；触发维度 ").append(k.label()).append("：").append(v.detail());
            }
        });
        if (!stale.isEmpty()) {
            sb.append("；注意：").append(stale).append(" 上游为旧版本(STALE)，结论仍据其生成");
        }
        if (!missing.isEmpty()) {
            sb.append("；缺失维度 ").append(missing);
        }
        return sb.toString();
    }
}
