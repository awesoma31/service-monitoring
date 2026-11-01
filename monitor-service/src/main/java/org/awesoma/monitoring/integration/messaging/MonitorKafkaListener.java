package org.awesoma.monitoring.integration.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.awesoma.integration.IntegrationEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "integration.check-results", name = "transport", havingValue = "kafka")
class MonitorKafkaListener {
    private final MonitorEventHandler handler;
    private final ObjectMapper json;

    @KafkaListener(topics = "checks.completed.v1", groupId = "monitor-check-results-v1")
    public void receive(String value) {
        try {
            handler.handle(json.readValue(value, IntegrationEvent.class));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid integration event JSON", e);
        }
    }
}
