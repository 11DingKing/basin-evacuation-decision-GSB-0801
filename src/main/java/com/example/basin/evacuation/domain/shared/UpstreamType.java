package com.example.basin.evacuation.domain.shared;

public enum UpstreamType {
    RAINFALL("降雨"),
    WATER_LEVEL("水位"),
    HAZARD("隐患点"),
    ROAD("道路");

    private final String label;

    UpstreamType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
