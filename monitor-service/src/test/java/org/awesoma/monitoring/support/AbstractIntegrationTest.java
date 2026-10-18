package org.awesoma.monitoring.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.awesoma.monitoring.integration.CheckHistoryClient;
import org.awesoma.monitoring.integration.NotificationClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Boots the application against the shared Postgres, with Liquibase applying the real
 * migrations. Hibernate runs in validate mode, so a mapping that disagrees with the
 * schema fails the context startup rather than surfacing later in production.
 */
@SpringBootTest
@org.springframework.context.annotation.Import(org.awesoma.security.TestJwtConfiguration.class)
@org.springframework.security.test.context.support.WithMockUser(username = "1", roles = "SUPERVISOR")
public abstract class AbstractIntegrationTest {
    @MockitoBean protected org.awesoma.monitoring.integration.UserClient userClient;
    protected final java.util.Map<Long, org.awesoma.security.UserSecurity> remoteSecurity = new java.util.HashMap<>();
    protected final java.util.Map<Long, org.awesoma.security.UserProfile> remoteProfiles = new java.util.HashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong USER_IDS = new java.util.concurrent.atomic.AtomicLong(1000);

    @org.junit.jupiter.api.BeforeEach
    void configureUserService() {
        org.mockito.Mockito.when(userClient.security(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(call -> {
            Long id = call.getArgument(0);
            return remoteSecurity.getOrDefault(id, new org.awesoma.security.UserSecurity(id, "VIEWER", "INACTIVE"));
        });
        org.mockito.Mockito.when(userClient.profiles(org.mockito.ArgumentMatchers.anyList())).thenAnswer(call -> {
            java.util.List<Long> ids = call.getArgument(0);
            return ids.stream().map(remoteProfiles::get).filter(java.util.Objects::nonNull).toList();
        });
    }

    protected Long remoteUser(String email) { return remoteUser(email, "VIEWER"); }
    protected Long remoteUser(String email, String role) {
        Long id = USER_IDS.incrementAndGet();
        remoteSecurity.put(id, new org.awesoma.security.UserSecurity(id, role, "ACTIVE"));
        remoteProfiles.put(id, new org.awesoma.security.UserProfile(id, email, "Test User"));
        return id;
    }

    // The other services are replaced by mocks of their Feign clients.
    @MockitoBean protected NotificationClient notificationClient;
    @MockitoBean protected CheckHistoryClient checkHistoryClient;

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
