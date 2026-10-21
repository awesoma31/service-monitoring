package org.awesoma.check.support;

import org.awesoma.security.JwtTokens;
import org.awesoma.security.ResourceAccessClient;
import org.awesoma.security.TestJwtConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Boots the service against the shared Postgres: Liquibase migrates it over JDBC, and the
 * service then reads and writes it over R2DBC, exactly as in the container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestJwtConfiguration.class)
public abstract class AbstractIntegrationTest {
    @Autowired
    protected WebTestClient http;
    @Autowired
    protected JwtTokens tokens;
    @MockitoBean
    protected ResourceAccessClient accessClient;

    @BeforeEach
    void authenticateRequests() {
        http = http.mutate().defaultHeader("Authorization", "Bearer " + tokens.issue("1", "SUPERVISOR").getTokenValue())
                .build();
        Mockito.when(accessClient.allowed(ArgumentMatchers.anyString(),
                ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(),
                ArgumentMatchers.anyBoolean())).thenReturn(true);
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
