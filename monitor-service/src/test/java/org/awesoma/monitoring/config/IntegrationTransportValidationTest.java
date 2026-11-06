package org.awesoma.monitoring.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class IntegrationTransportValidationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(IntegrationTransportValidation.class);

    @Test
    void rejectsUnknownTransportAtStartup() {
        for (String flow : java.util.List.of("check-results", "incident-events", "resource-events")) {
            context.withPropertyValues("integration." + flow + ".transport=oops")
                    .run(app -> assertThat(app).hasFailed());
        }
        context.withPropertyValues("integration.check-results.transport=kafka", "integration.incident-events.transport=http")
                .run(app -> assertThat(app).hasNotFailed());
    }
}
