package org.awesoma.notification.adapters.in.messaging;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.awesoma.notification.application.usecase.NotificationEventHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.awesoma.integration.IntegrationEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@UtilityClass
@Slf4j
final class NotificationKafkaListeners {

    @Component
    @RequiredArgsConstructor
    @ConditionalOnProperty(prefix = "integration.incident-events", name = "transport", havingValue = "kafka")
    static class Incidents {
        private final NotificationEventHandler handler;
        private final ObjectMapper json;

        @KafkaListener(topics = "incidents.events.v1", groupId = "notification-incidents-v1")
        public void receive(ConsumerRecord<String, String> record) {
            handle(json, handler, record, IntegrationEvent.INCIDENT_CHANGED, "notification-incidents-v1");
        }
    }

    @Component
    @RequiredArgsConstructor
    @ConditionalOnProperty(prefix = "integration.resource-events", name = "transport", havingValue = "kafka")
    static class Resources {
        private final NotificationEventHandler handler;
        private final ObjectMapper json;

        @KafkaListener(topics = "resources.events.v1", groupId = "notification-resource-events-v1")
        public void receive(ConsumerRecord<String, String> record) {
            handle(json, handler, record, null, "notification-resource-events-v1");
        }
    }

    @Component
    @RequiredArgsConstructor
    @ConditionalOnProperty(prefix = "integration.report-events", name = "transport", havingValue = "kafka")
    static class Reports {
        private final NotificationEventHandler handler;
        private final ObjectMapper json;

        @KafkaListener(topics = "reports.events.v1", groupId = "notification-reports-v1")
        public void receive(ConsumerRecord<String, String> record) {
            handle(json, handler, record, IntegrationEvent.REPORT_UPLOADED, "notification-reports-v1");
        }
    }

    private static void handle(ObjectMapper json, NotificationEventHandler handler, ConsumerRecord<String, String> record,
            String expectedType, String group) {
        IntegrationEvent event = null;
        log.debug("Kafka record received: topic={} partition={} offset={} group={}",
                record.topic(), record.partition(), record.offset(), group);
        try {
            event = json.readValue(record.value(), IntegrationEvent.class);
            if (event == null) throw new IllegalArgumentException("Missing event");
            if (expectedType != null && !expectedType.equals(event.type()))
                throw new IllegalArgumentException("Unexpected event type");
            if (expectedType == null && !IntegrationEvent.PROJECT_DELETED.equals(event.type())
                    && !IntegrationEvent.MONITOR_DELETED.equals(event.type()))
                throw new IllegalArgumentException("Unexpected event type");
            handler.handle(ResourceEventMapper.from(event)).block();
            log.info("Kafka event handled: topic={} partition={} offset={} group={} event_id={} type={} project_id={} incident_id={}",
                    record.topic(), record.partition(), record.offset(), group, event.eventId(), event.type(), event.projectId(), event.incidentId());
        } catch (JsonProcessingException e) {
            log.warn("Invalid Kafka event JSON: topic={} partition={} offset={} group={}",
                    record.topic(), record.partition(), record.offset(), group);
            throw new IllegalArgumentException("Invalid event JSON", e);
        } catch (RuntimeException e) {
            log.warn("Kafka event handling failed: topic={} partition={} offset={} group={} event_id={} error_type={}",
                    record.topic(), record.partition(), record.offset(), group, event == null ? null : event.eventId(), e.getClass().getSimpleName());
            throw e;
        }
    }
}
