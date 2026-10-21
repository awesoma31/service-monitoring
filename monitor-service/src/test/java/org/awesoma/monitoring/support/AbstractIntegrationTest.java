package org.awesoma.monitoring.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.awesoma.monitoring.integration.CheckHistoryClient;
import org.awesoma.monitoring.integration.NotificationClient;
import org.awesoma.monitoring.integration.UserClient;
import org.awesoma.security.TestJwtConfiguration;
import org.awesoma.security.UserProfile;
import org.awesoma.security.UserSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Boots the application against the shared Postgres, with Liquibase applying the real
 * migrations. Hibernate runs in validate mode, so a mapping that disagrees with the
 * schema fails the context startup rather than surfacing later in production.
 */
@SpringBootTest
@Import(TestJwtConfiguration.class)
@WithMockUser(username = "1", roles = "SUPERVISOR")
public abstract class AbstractIntegrationTest {
    @MockitoBean protected UserClient userClient;
    protected final Map<Long, UserSecurity> remoteSecurity = new HashMap<>();
    protected final Map<Long, UserProfile> remoteProfiles = new HashMap<>();
    private static final AtomicLong USER_IDS = new AtomicLong(1000);

    @BeforeEach
    void configureUserService() {
        Mockito.when(userClient.security(ArgumentMatchers.anyLong())).thenAnswer(call -> {
            Long id = call.getArgument(0);
            return remoteSecurity.getOrDefault(id, new UserSecurity(id, "VIEWER", "INACTIVE"));
        });
        Mockito.when(userClient.profiles(ArgumentMatchers.anyList())).thenAnswer(call -> {
            List<Long> ids = call.getArgument(0);
            return ids.stream().map(remoteProfiles::get).filter(Objects::nonNull).toList();
        });
    }

    protected Long remoteUser(String email) { return remoteUser(email, "VIEWER"); }
    protected Long remoteUser(String email, String role) {
        Long id = USER_IDS.incrementAndGet();
        remoteSecurity.put(id, new UserSecurity(id, role, "ACTIVE"));
        remoteProfiles.put(id, new UserProfile(id, email, "Test User"));
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
