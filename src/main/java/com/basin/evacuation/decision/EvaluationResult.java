package com.basin.evacuation.decision;

import java.util.List;

/**
 * 阈值评估结果：结论 + 人可读的理由（触发规则、道路提示、上游异常提示）。
 */
public record EvaluationResult(DecisionOutcome outcome, List<String> reasons) {}
