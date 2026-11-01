package org.awesoma.monitoring.integration.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.awesoma.monitoring.integration.CheckHistoryClient;
import org.awesoma.monitoring.integration.NotificationClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

class MonitorPublisherConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(MonitorKafkaPublishers.class)
            .withBean(NotificationClient.class, () -> mock(NotificationClient.class))
            .withBean(CheckHistoryClient.class, () -> mock(CheckHistoryClient.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class));

    @Test
    void selectsHttpByDefaultAndAllowsMixedTransports() {
        context.run(app -> {
            assertThat(app.getBean(IncidentEventPublisher.class).transport()).isEqualTo("http");
            assertThat(app.getBean(ResourceEventPublisher.class).transport()).isEqualTo("http");
        });
        context.withPropertyValues("integration.incident-events.transport=kafka", "integration.resource-events.transport=http")
                .run(app -> {
                    assertThat(app.getBean(IncidentEventPublisher.class).transport()).isEqualTo("kafka");
                    assertThat(app.getBean(ResourceEventPublisher.class).transport()).isEqualTo("http");
                });
        context.withPropertyValues("integration.incident-events.transport=http", "integration.resource-events.transport=kafka")
                .run(app -> {
                    assertThat(app.getBean(IncidentEventPublisher.class).transport()).isEqualTo("http");
                    assertThat(app.getBean(ResourceEventPublisher.class).transport()).isEqualTo("kafka");
                });
    }
}
