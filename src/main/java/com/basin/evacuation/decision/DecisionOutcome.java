package com.basin.evacuation.decision;

/**
 * 决策结果。四个风险等级 + 一个独立结果「数据不足」。
 * 数据不足（INSUFFICIENT_DATA）不是等级，与低风险（LOW_RISK）语义完全不同：
 * 前者表示核心证据缺失、无法评估；后者表示证据齐全且全部低于阈值。
 */
public enum DecisionOutcome {

    EVACUATE_NOW(1, "一级·立即转移"),
    PRE_TRANSFER(2, "二级·预转移"),
    PREPARE(3, "三级·准备"),
    LOW_RISK(4, "四级·低风险"),
    INSUFFICIENT_DATA(0, "数据不足");

    /** 优先级：数字越小越紧急；0 表示非风险等级 */
    private final int priority;
    private final String label;

    DecisionOutcome(int priority, String label) {
        this.priority = priority;
        this.label = label;
    }

    public int priority() {
        return priority;
    }

    public String label() {
        return label;
    }

    public boolean isRiskLevel() {
        return priority > 0;
    }
}
