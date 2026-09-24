package org.awesoma.monitoring.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.awesoma.monitoring.web.access.RoleAuthorizationInterceptor;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boots the application against the shared Postgres, with Liquibase applying the real
 * migrations. Hibernate runs in validate mode, so a mapping that disagrees with the
 * schema fails the context startup rather than surfacing later in production.
 */
@SpringBootTest
@Import(AbstractIntegrationTest.OwnerRoleHeaderConfig.class)
public abstract class AbstractIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class OwnerRoleHeaderConfig {

        @Bean
        MockMvcBuilderCustomizer ownerRoleHeader() {
            return builder -> builder.defaultRequest(get("/")
                    .header(RoleAuthorizationInterceptor.ROLE_HEADER, "OWNER"));
        }
    }

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
