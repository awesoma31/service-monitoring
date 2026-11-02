package org.awesoma.report.adapters.in.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.awesoma.integration.IntegrationEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads the existing resources.events.v1 topic in a group of its own, so the other services
 * keep receiving their copies. The offset is committed only after the local transaction.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "integration.resource-events", name = "transport", havingValue = "kafka")
class ResourceEventsKafkaListener {
    private final ResourceEventReceiver receiver;
    private final ObjectMapper json;

    @KafkaListener(topics = "resources.events.v1", groupId = "report-resource-events-v1")
    void receive(String value) {
        try {
            receiver.receive(json.readValue(value, IntegrationEvent.class));
        } catch (JsonProcessingException malformed) {
            throw new IllegalArgumentException("Invalid event JSON", malformed);
        }
    }
}
