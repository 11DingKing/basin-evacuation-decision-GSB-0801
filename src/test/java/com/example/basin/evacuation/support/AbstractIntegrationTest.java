package com.example.basin.evacuation.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;

@Import(AbstractIntegrationTest.TestInfrastructureConfiguration.class)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("basin_evac")
                    .withUsername("basin")
                    .withPassword("basin")
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    protected static final Instant FIXED_NOW = Instant.parse("2026-07-29T03:30:00Z");

    @TestConfiguration
    static class TestInfrastructureConfiguration {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgresContainer() {
            return POSTGRES;
        }

        @Bean
        public TestClock testClock() {
            return new TestClock(FIXED_NOW);
        }

        @Bean
        @Primary
        public Clock clock(TestClock testClock) {
            return testClock;
        }
    }
}
