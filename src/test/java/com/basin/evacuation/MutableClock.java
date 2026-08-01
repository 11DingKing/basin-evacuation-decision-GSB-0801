package com.basin.evacuation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** 测试中可随意拨动的时钟，用于覆盖覆写生效前 / 恰好到期 / 到期后一瞬间三个时刻。 */
public class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public static MutableClock now() {
        return new MutableClock(Instant.now(), ZoneId.of("Asia/Shanghai"));
    }

    public void set(Instant newInstant) {
        this.instant = newInstant;
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }
}
