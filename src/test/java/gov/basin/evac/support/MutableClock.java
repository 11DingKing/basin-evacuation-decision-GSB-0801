package gov.basin.evac.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A {@link Clock} whose instant can be moved at will, so tests can freeze time exactly before,
 * at, and just after an override's expiry, and drive recompute timing deterministically.
 */
public class MutableClock extends Clock {

    private volatile Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant) {
        this(instant, ZoneId.of("UTC"));
    }

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
