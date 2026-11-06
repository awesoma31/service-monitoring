package org.awesoma.check.adapters.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.awesoma.integration.IntegrationEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "integration.resource-events", name = "transport", havingValue = "kafka")
class CheckKafkaListener {
    private final CheckEventHandler handler;
    private final ObjectMapper json;
    @KafkaListener(topics = "resources.events.v1", groupId = "check-resource-events-v1")
    public void receive(String value) {
        try { handler.handle(json.readValue(value, IntegrationEvent.class)).block(); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid event JSON", e); }
    }
}
