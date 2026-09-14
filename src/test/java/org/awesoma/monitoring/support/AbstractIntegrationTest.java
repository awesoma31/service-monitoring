package org.awesoma.monitoring.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boots the application against the shared Postgres, with Liquibase applying the real
 * migrations. Hibernate runs in validate mode, so a mapping that disagrees with the
 * schema fails the context startup rather than surfacing later in production.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", PostgresContainer.INSTANCE::getPassword);
        // The scheduler stays off: tests drive checks explicitly so that a background tick
        // cannot change the state they are asserting on.
        registry.add("checker.enabled", () -> false);
        registry.add("checker.interval-ms", () -> 60_000);
        registry.add("checker.batch-size", () -> 10);
    }
}
