package com.example.basin.evacuation.domain.shared;

public enum HealthStatus {
    HEALTHY,
    STALE,
    TIMEOUT,
    UNAVAILABLE;

    public boolean isUsable() {
        return this == HEALTHY || this == STALE;
    }

    public boolean isDown() {
        return this == TIMEOUT || this == UNAVAILABLE;
    }
}
