package org.awesoma.check.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boots the service against the shared Postgres: Liquibase migrates it over JDBC, and the
 * service then reads and writes it over R2DBC, exactly as in the container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
@org.springframework.context.annotation.Import(org.awesoma.security.TestJwtConfiguration.class)
public abstract class AbstractIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired
    protected org.springframework.test.web.reactive.server.WebTestClient http;
    @org.springframework.beans.factory.annotation.Autowired
    protected org.awesoma.security.JwtTokens tokens;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    protected org.awesoma.security.ResourceAccessClient accessClient;

    @org.junit.jupiter.api.BeforeEach
    void authenticateRequests() {
        http = http.mutate().defaultHeader("Authorization", "Bearer " + tokens.issue("1", "SUPERVISOR").getTokenValue()).build();
        org.mockito.Mockito.when(accessClient.allowed(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(true);
    }

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
