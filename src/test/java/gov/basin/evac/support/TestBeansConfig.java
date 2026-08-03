package gov.basin.evac.support;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Supplies the injectable {@link MutableClock} and {@link ControllableNotificationSender} as the
 * primary beans for integration tests, replacing the production system clock and logging sender.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestBeansConfig {

    // A fixed, deterministic anchor time for the whole suite.
    public static final Instant ANCHOR = Instant.parse("2026-07-29T03:05:00Z");

    @Bean
    public MutableClock mutableClock() {
        return new MutableClock(ANCHOR);
    }

    @Bean
    @Primary
    public Clock clock(MutableClock mutableClock) {
        return mutableClock;
    }

    @Bean
    @Primary
    public ControllableNotificationSender controllableNotificationSender() {
        return new ControllableNotificationSender();
    }
}
