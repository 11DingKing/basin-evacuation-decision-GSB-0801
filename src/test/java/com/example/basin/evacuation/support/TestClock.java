package com.example.basin.evacuation.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Mutable {@link Clock} used in tests to pin time at override expiry boundaries
 * (before / exactly at / just after) and to advance it deterministically.
 */
public class TestClock extends Clock {

    private Instant instant;
    private ZoneId zone = ZoneOffset.UTC;

    public TestClock(Instant instant) {
        this.instant = instant;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    public void advanceMillis(long millis) {
        this.instant = instant.plusMillis(millis);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        this.zone = zone;
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
