package com.basin.evacuation.decision;

import com.basin.evacuation.config.ThresholdProperties;
import com.basin.evacuation.snapshot.HazardPointStatus;
import com.basin.evacuation.snapshot.RiskSnapshot;
import com.basin.evacuation.snapshot.RoadStatus;
import com.basin.evacuation.snapshot.UpstreamHealth;
import com.basin.evacuation.snapshot.UpstreamKind;
import com.basin.evacuation.snapshot.UpstreamStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 阈值评估器（decision 领域模块内唯一的决策规则所在地）。
 * 控制器与服务不包含任何决策规则，仅调用本组件。
 *
 * 规则（按优先级从高到低判定，命中即定级）：
 *  R0 核心证据（降水/水位/隐患点/主路）缺失或未知 -> 数据不足（非等级）
 *  R1 隐患点 CRITICAL，或 降水>=red 且 水位>=red            -> 一级·立即转移
 *  R2 隐患点 WARNING， 或 降水>=orange 且 水位>=orange        -> 二级·预转移
 *  R3 降水>=yellow 或 水位>=yellow                            -> 三级·准备
 *  R4 其余（证据齐全且全部低于阈值）                           -> 四级·低风险
 *
 * 道路与脆弱人群不改变定级，但会作为理由附加；上游 STALE/TIMEOUT/ERROR 也会附加说明。
 * 阈值一律为「大于等于」闭区间。
 */
@Component
public class ThresholdEvaluator {

    private final ThresholdProperties thresholds;

    public ThresholdEvaluator(ThresholdProperties thresholds) {
        this.thresholds = thresholds;
    }

    public EvaluationResult evaluate(RiskSnapshot s) {
        List<String> upstreamNotes = upstreamNotes(s);

        // R0：核心证据缺失 -> 数据不足（与低风险完全不同）
        List<String> missing = new ArrayList<>();
        if (s.getRainfall3hMm() == null) {
            missing.add("3小时累计降水");
        }
        if (s.getWaterLevelM() == null) {
            missing.add("河道水位");
        }
        if (s.getHazardPointStatus() == null || s.getHazardPointStatus() == HazardPointStatus.UNKNOWN) {
            missing.add("隐患点状态");
        }
        if (s.getPrimaryRoadStatus() == null || s.getPrimaryRoadStatus() == RoadStatus.UNKNOWN) {
            missing.add("主路状态");
        }
        if (!missing.isEmpty()) {
            List<String> reasons = new ArrayList<>();
            reasons.add("核心证据缺失：" + String.join("、", missing) + "，无法评估风险等级，结论为数据不足");
            reasons.addAll(upstreamNotes);
            return new EvaluationResult(DecisionOutcome.INSUFFICIENT_DATA, List.copyOf(reasons));
        }

        BigDecimal rain = s.getRainfall3hMm();
        BigDecimal water = s.getWaterLevelM();
        var rainT = thresholds.rainfall();
        var waterT = thresholds.water();

        DecisionOutcome outcome;
        String trigger;
        if (s.getHazardPointStatus() == HazardPointStatus.CRITICAL) {
            outcome = DecisionOutcome.EVACUATE_NOW;
            trigger = "达到一级（立即转移）阈值：隐患点状态为CRITICAL";
        } else if (ge(rain, rainT.red()) && ge(water, waterT.red())) {
            outcome = DecisionOutcome.EVACUATE_NOW;
            trigger = "达到一级（立即转移）阈值：3小时累计降水" + n(rain) + "mm≥" + n(rainT.red())
                    + "mm且河道水位" + n(water) + "m≥" + n(waterT.red()) + "m";
        } else if (s.getHazardPointStatus() == HazardPointStatus.WARNING) {
            outcome = DecisionOutcome.PRE_TRANSFER;
            trigger = "达到二级（预转移）阈值：隐患点状态为WARNING";
        } else if (ge(rain, rainT.orange()) && ge(water, waterT.orange())) {
            outcome = DecisionOutcome.PRE_TRANSFER;
            trigger = "达到二级（预转移）阈值：3小时累计降水" + n(rain) + "mm≥" + n(rainT.orange())
                    + "mm且河道水位" + n(water) + "m≥" + n(waterT.orange()) + "m";
        } else if (ge(rain, rainT.yellow())) {
            outcome = DecisionOutcome.PREPARE;
            trigger = "达到三级（准备）阈值：3小时累计降水" + n(rain) + "mm≥" + n(rainT.yellow()) + "mm";
        } else if (ge(water, waterT.yellow())) {
            outcome = DecisionOutcome.PREPARE;
            trigger = "达到三级（准备）阈值：河道水位" + n(water) + "m≥" + n(waterT.yellow()) + "m";
        } else {
            outcome = DecisionOutcome.LOW_RISK;
            trigger = "全部指标低于三级阈值：判定为低风险";
        }

        List<String> reasons = new ArrayList<>();
        reasons.add(trigger);
        reasons.addAll(roadAndPopulationNotes(s));
        reasons.addAll(upstreamNotes);
        return new EvaluationResult(outcome, List.copyOf(reasons));
    }

    private static List<String> roadAndPopulationNotes(RiskSnapshot s) {
        List<String> notes = new ArrayList<>();
        if (s.getPrimaryRoadStatus() == RoadStatus.CLOSED) {
            notes.add("主路封闭：撤离路线须避开主路");
        }
        if (s.getSecondaryRoadStatus() == RoadStatus.UNKNOWN) {
            notes.add("次路状态未知：需人工核实备用路线");
        } else if (s.getSecondaryRoadStatus() == RoadStatus.CLOSED) {
            notes.add("次路封闭：备用路线受限");
        }
        if (s.getVulnerablePopulation() > 0) {
            notes.add("脆弱人群" + s.getVulnerablePopulation() + "人：须优先安排转运");
        }
        return notes;
    }

    private static List<String> upstreamNotes(RiskSnapshot s) {
        List<String> notes = new ArrayList<>();
        for (UpstreamKind kind : UpstreamKind.values()) {
            UpstreamHealth h = s.getUpstreamHealth() == null ? null : s.getUpstreamHealth().get(kind);
            if (h == null || h.status() == null || h.status() == UpstreamStatus.OK) {
                continue;
            }
            switch (h.status()) {
                case STALE -> notes.add("上游" + label(kind) + "返回旧版本数据（version=" + h.version() + "），结果可信度降低");
                case TIMEOUT -> notes.add("上游" + label(kind) + "超时，本次未获取到数据");
                case ERROR -> notes.add("上游" + label(kind) + "返回错误" + (h.detail() == null ? "" : "：" + h.detail()));
                default -> { }
            }
        }
        return notes;
    }

    private static String label(UpstreamKind kind) {
        return switch (kind) {
            case RAINFALL -> "降雨";
            case WATER_LEVEL -> "水位";
            case HAZARD_POINT -> "隐患点";
            case ROAD -> "道路";
        };
    }

    private static boolean ge(BigDecimal actual, BigDecimal threshold) {
        return actual.compareTo(threshold) >= 0;
    }

    private static String n(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
