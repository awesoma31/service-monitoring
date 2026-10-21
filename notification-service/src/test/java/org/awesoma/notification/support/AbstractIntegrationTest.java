package org.awesoma.notification.support;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import org.awesoma.notification.client.MonitorClient;
import org.awesoma.security.JwtTokens;
import org.awesoma.security.ResourceAccessClient;
import org.awesoma.security.TestJwtConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Boots the service against the shared Postgres with the real migrations. monitor-service is
 * replaced by a mock of its Feign client; by default every project and incident exists.
 * Tests use their own project ids instead of rolling back, since requests cross threads.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestJwtConfiguration.class)
public abstract class AbstractIntegrationTest {
    @Autowired
    protected WebTestClient http;
    @Autowired
    protected JwtTokens tokens;
    @MockitoBean protected ResourceAccessClient accessClient;

    @MockitoBean protected MonitorClient monitorService;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", PostgresContainer.INSTANCE::getPassword);
    }

    @BeforeEach
    void everythingExistsUnlessStatedOtherwise() {
        http = http.mutate().defaultHeader("Authorization", "Bearer " + tokens.issue("1", "SUPERVISOR").getTokenValue())
                .build();
        when(accessClient.allowed(ArgumentMatchers.anyString(), anyLong(), anyLong(),
                ArgumentMatchers.anyBoolean())).thenReturn(true);
        when(monitorService.projectExists(anyLong())).thenReturn(true);
        when(monitorService.incidentExists(anyLong())).thenReturn(true);
    }
}
