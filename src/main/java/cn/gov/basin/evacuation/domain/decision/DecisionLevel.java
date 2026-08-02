package cn.gov.basin.evacuation.domain.decision;

public enum DecisionLevel {
    LEVEL_1,
    LEVEL_2,
    LEVEL_3,
    LEVEL_4,
    INSUFFICIENT_DATA;

    public int severity() {
        return switch (this) {
            case LEVEL_1 -> 1;
            case LEVEL_2 -> 2;
            case LEVEL_3 -> 3;
            case LEVEL_4 -> 4;
            case INSUFFICIENT_DATA -> 0;
        };
    }
}
