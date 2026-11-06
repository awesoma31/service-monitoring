package org.awesoma.monitoring.adapters.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.awesoma.integration.IntegrationEvent;
import org.awesoma.monitoring.adapters.out.http.CheckHistoryClient;
import org.awesoma.monitoring.adapters.out.http.NotificationClient;
import org.awesoma.monitoring.adapters.out.http.ReportClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

class MonitorPublisherConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(MonitorKafkaPublishers.class)
            .withBean(NotificationClient.class, () -> mock(NotificationClient.class))
            .withBean(CheckHistoryClient.class, () -> mock(CheckHistoryClient.class))
            .withBean(ReportClient.class, () -> mock(ReportClient.class))
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

    @Test
    void overHttpOnlyAProjectDeletionReachesReportService() {
        context.run(app -> {
            var publisher = app.getBean(ResourceEventPublisher.class);
            var reports = app.getBean(ReportClient.class);
            var projectDeleted = new IntegrationEvent(UUID.randomUUID(), 1, IntegrationEvent.PROJECT_DELETED,
                    OffsetDateTime.now(), null, 42L, null, null, List.of(7L), null);
            var monitorDeleted = new IntegrationEvent(UUID.randomUUID(), 1, IntegrationEvent.MONITOR_DELETED,
                    OffsetDateTime.now(), 7L, null, null, null, null, null);

            publisher.publish(monitorDeleted);
            verify(reports, never()).integrationEvent(any());
            publisher.publish(projectDeleted);
            verify(reports).integrationEvent(projectDeleted);
            verify(app.getBean(NotificationClient.class)).integrationEvent(projectDeleted);
        });
    }
}
