package com.basin.evacuation;

import com.basin.evacuation.snapshot.UpstreamHealth;
import com.basin.evacuation.snapshot.UpstreamKind;
import com.basin.evacuation.snapshot.UpstreamStatus;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 集成测试基类：单例 PostgreSQL 容器（真实 PostgreSQL，Flyway 全量重放迁移）。
 */
@SpringBootTest
@Import(ClockTestConfig.class)
public abstract class PostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDataSourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    protected static Map<UpstreamKind, UpstreamHealth> allOkUpstream() {
        Map<UpstreamKind, UpstreamHealth> health = new EnumMap<>(UpstreamKind.class);
        for (UpstreamKind kind : UpstreamKind.values()) {
            health.put(kind, new UpstreamHealth(UpstreamStatus.OK, kind.name().toLowerCase() + "-v1", null));
        }
        return health;
    }
}
