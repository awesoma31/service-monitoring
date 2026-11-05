package org.awesoma.monitoring.integration.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.TimeUnit;
import org.awesoma.integration.IntegrationEvent;
import org.awesoma.monitoring.integration.CheckHistoryClient;
import org.awesoma.monitoring.integration.NotificationClient;
import org.awesoma.monitoring.integration.ReportClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration(proxyBeanMethods = false)
@Slf4j
class MonitorKafkaPublishers {
    @Bean
    @ConditionalOnProperty(prefix = "integration.incident-events", name = "transport", havingValue = "http", matchIfMissing = true)
    IncidentEventPublisher httpIncidentPublisher(NotificationClient client) {
        return new IncidentEventPublisher() {
            public String transport() { return "http"; }
            public void publish(IntegrationEvent event) { client.integrationEvent(event); }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "integration.incident-events", name = "transport", havingValue = "kafka")
    IncidentEventPublisher kafkaIncidentPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json) {
        return new IncidentEventPublisher() {
            public String transport() { return "kafka"; }
            public void publish(IntegrationEvent event) {
                send(kafka, json, "incidents.events.v1", event.incidentId().toString(), event);
            }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "integration.resource-events", name = "transport", havingValue = "http", matchIfMissing = true)
    ResourceEventPublisher httpResourcePublisher(CheckHistoryClient check, NotificationClient notifications,
            ReportClient reports) {
        return new ResourceEventPublisher() {
            public String transport() { return "http"; }
            // A failure is retried for every receiver; each skips an event ID it has already handled.
            public void publish(IntegrationEvent event) {
                check.integrationEvent(event);
                if (IntegrationEvent.PROJECT_DELETED.equals(event.type())) {
                    notifications.integrationEvent(event);
                    reports.integrationEvent(event);
                }
            }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "integration.resource-events", name = "transport", havingValue = "kafka")
    ResourceEventPublisher kafkaResourcePublisher(KafkaTemplate<String, String> kafka, ObjectMapper json) {
        return new ResourceEventPublisher() {
            public String transport() { return "kafka"; }
            public void publish(IntegrationEvent event) {
                String key = event.projectId() != null ? "project-" + event.projectId() : "monitor-" + event.monitorId();
                send(kafka, json, "resources.events.v1", key, event);
            }
        };
    }

    static void send(KafkaTemplate<String, String> kafka, ObjectMapper json, String topic, String key, IntegrationEvent event) {
        try {
            var result = kafka.send(topic, key, json.writeValueAsString(event)).get(20, TimeUnit.SECONDS);
            var metadata = result.getRecordMetadata();
            log.info("Kafka event published: topic={} partition={} offset={} event_id={} type={} project_id={} incident_id={} monitor_id={}",
                    topic, metadata.partition(), metadata.offset(), event.eventId(), event.type(), event.projectId(), event.incidentId(), event.monitorId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Kafka event publication interrupted: topic={} event_id={} type={}", topic, event.eventId(), event.type());
            throw new IllegalStateException("Interrupted publishing integration event", e);
        } catch (Exception e) {
            log.warn("Kafka event publication failed: topic={} event_id={} type={} error_type={}",
                    topic, event.eventId(), event.type(), e.getClass().getSimpleName());
            throw new IllegalStateException("Cannot publish integration event", e);
        }
    }
}
