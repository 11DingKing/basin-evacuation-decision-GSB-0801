package com.example.basin.evacuation.domain.decision;

public enum Dimension {
    RAINFALL("降雨"),
    WATER_LEVEL("水位"),
    HAZARD("隐患点"),
    ROAD("道路"),
    EXPOSURE("脆弱人群暴露");

    private final String label;

    Dimension(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
