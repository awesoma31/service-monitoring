package org.awesoma.notification.adapters.in.messaging;

import org.awesoma.notification.application.usecase.NotificationEventHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.awesoma.integration.IntegrationEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

final class NotificationKafkaListeners {
    private NotificationKafkaListeners() {}

    @Component
    @RequiredArgsConstructor
    @ConditionalOnProperty(prefix = "integration.incident-events", name = "transport", havingValue = "kafka")
    static class Incidents {
        private final NotificationEventHandler handler;
        private final ObjectMapper json;
        @KafkaListener(topics = "incidents.events.v1", groupId = "notification-incidents-v1")
        public void receive(String value) { handle(json, handler, value, IntegrationEvent.INCIDENT_CHANGED); }
    }

    @Component
    @RequiredArgsConstructor
    @ConditionalOnProperty(prefix = "integration.resource-events", name = "transport", havingValue = "kafka")
    static class Resources {
        private final NotificationEventHandler handler;
        private final ObjectMapper json;
        @KafkaListener(topics = "resources.events.v1", groupId = "notification-resource-events-v1")
        public void receive(String value) { handle(json, handler, value, null); }
    }

    private static void handle(ObjectMapper json, NotificationEventHandler handler, String value, String expectedType) {
        try {
            IntegrationEvent event = json.readValue(value, IntegrationEvent.class);
            if (event == null) throw new IllegalArgumentException("Missing event");
            if (expectedType != null && !expectedType.equals(event.type())) throw new IllegalArgumentException("Unexpected event type");
            if (expectedType == null && !IntegrationEvent.PROJECT_DELETED.equals(event.type())
                    && !IntegrationEvent.MONITOR_DELETED.equals(event.type())) throw new IllegalArgumentException("Unexpected event type");
            handler.handle(ResourceEventMapper.from(event)).block();
        } catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid event JSON", e); }
    }
}
