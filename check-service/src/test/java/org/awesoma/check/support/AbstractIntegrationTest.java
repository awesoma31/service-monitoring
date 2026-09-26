package org.awesoma.check.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boots the service against the shared Postgres: Liquibase migrates it over JDBC, and the
 * service then reads and writes it over R2DBC, exactly as in the container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        var db = PostgresContainer.INSTANCE;
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://%s:%d/%s"
                .formatted(db.getHost(), db.getFirstMappedPort(), db.getDatabaseName()));
        registry.add("spring.r2dbc.username", db::getUsername);
        registry.add("spring.r2dbc.password", db::getPassword);
        registry.add("spring.liquibase.url", db::getJdbcUrl);
        registry.add("spring.liquibase.user", db::getUsername);
        registry.add("spring.liquibase.password", db::getPassword);
        // Tests drive checks explicitly; a background pass would race with their assertions.
        registry.add("checker.enabled", () -> false);
    }
}
