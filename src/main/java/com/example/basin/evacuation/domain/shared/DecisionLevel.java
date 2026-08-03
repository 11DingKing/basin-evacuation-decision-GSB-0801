package com.example.basin.evacuation.domain.shared;

public enum DecisionLevel {
    BLUE(1, "蓝色 / 低风险"),
    YELLOW(2, "黄色 / 中等风险"),
    ORANGE(3, "橙色 / 高风险"),
    RED(4, "红色 / 极高风险"),
    INSUFFICIENT_DATA(0, "数据不足");

    private final int rank;
    private final String label;

    DecisionLevel(int rank, String label) {
        this.rank = rank;
        this.label = label;
    }

    public int rank() {
        return rank;
    }

    public String label() {
        return label;
    }

    public boolean isRiskLevel() {
        return this != INSUFFICIENT_DATA;
    }

    public static DecisionLevel max(DecisionLevel a, DecisionLevel b) {
        if (a == INSUFFICIENT_DATA) return b;
        if (b == INSUFFICIENT_DATA) return a;
        return a.rank >= b.rank ? a : b;
    }
}
