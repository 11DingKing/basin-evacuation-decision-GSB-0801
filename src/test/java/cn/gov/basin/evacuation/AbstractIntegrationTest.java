package cn.gov.basin.evacuation;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    public static final MutableClock TEST_CLOCK =
            new MutableClock(Clock.fixed(Instant.parse("2026-07-29T03:05:00Z"), ZoneOffset.UTC));

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected DataSource dataSource;

    @BeforeEach
    void resetDatabaseState() {
        jdbc.execute("TRUNCATE TABLE notification_outbox, decision_advices, manual_overrides, "
                + "risk_snapshots RESTART IDENTITY CASCADE");
        jdbc.update("""
                INSERT INTO risk_snapshots(
                    snapshot_id, region_code, observed_at,
                    rainfall_3h_mm, water_level_m, hazard_status,
                    primary_road_status, secondary_road_status, vulnerable_population,
                    rainfall_health, water_level_health, hazard_health, infrastructure_health,
                    rainfall_version, water_level_version, hazard_version, infrastructure_version
                ) VALUES
                    ('sc-510182-20260729T0300','510182','2026-07-29T03:00:00Z',
                     118.00, 6.12, 'WARNING',
                     'CLOSED','UNKNOWN',286,
                     'OK','OK','OK','OK',
                     'rain-v20260729.0300','wl-v20260729.0300',
                     'haz-v20260729.0300','infra-v20260729.0300'),
                    ('sc-510182-20260729T0600','510182','2026-07-29T06:00:00Z',
                     164.00, 6.18, 'WARNING',
                     'OPEN','UNKNOWN',286,
                     'OK','OK','OK','OK',
                     'rain-20260729T0600','wl-20260729T0600',
                     'haz-20260729T0600','infra-20260729T0600')
                """);
        setClock(Instant.parse("2026-07-29T03:05:00Z"));
    }

    protected static void setClock(Instant instant) {
        TEST_CLOCK.setDelegate(Clock.fixed(instant, ZoneOffset.UTC));
    }

    protected static void tickSeconds(long seconds) {
        TEST_CLOCK.setDelegate(Clock.fixed(
                TEST_CLOCK.instant().plusSeconds(seconds), ZoneOffset.UTC));
    }

    @TestConfiguration
    public static class TestClockConfig {
        @Bean
        @Primary
        public Clock testClock() {
            return TEST_CLOCK;
        }
    }

    public static final class MutableClock extends Clock {
        private Clock delegate;

        public MutableClock(Clock delegate) {
            this.delegate = delegate;
        }

        public void setDelegate(Clock clock) {
            this.delegate = clock;
        }

        @Override
        public ZoneId getZone() {
            return delegate.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return delegate.withZone(zone);
        }

        @Override
        public Instant instant() {
            return delegate.instant();
        }
    }
}
