package gov.basin.evac.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides an injectable {@link Clock} so that override validity windows and recompute
 * timing can be driven deterministically in tests (frozen before / at / after expiry).
 * Production uses a UTC system clock; tests supply their own {@code Clock} bean, which this
 * definition backs off for via {@link ConditionalOnMissingBean}.
 */
@Configuration
public class ClockConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
