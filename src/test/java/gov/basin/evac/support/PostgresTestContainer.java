package gov.basin.evac.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shares a single real PostgreSQL container across the integration test suite. Using a genuine
 * PostgreSQL (not H2) is essential: the immutability/no-delete guarantees, JSON-free schema,
 * sequences and row-level locking behave exactly as in production, so replay guarantees are
 * proven against the real engine.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainer {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }
}
