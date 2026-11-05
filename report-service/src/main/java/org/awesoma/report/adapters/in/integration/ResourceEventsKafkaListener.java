package org.awesoma.report.adapters.in.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
@Slf4j
@ConditionalOnProperty(prefix = "integration.resource-events", name = "transport", havingValue = "kafka")
class ResourceEventsKafkaListener {
    private final ResourceEventReceiver receiver;
    private final ObjectMapper json;

    @KafkaListener(topics = "resources.events.v1", groupId = "report-resource-events-v1")
    void receive(ConsumerRecord<String, String> record) {
        IntegrationEvent event = null;
        log.debug("Kafka record received: topic={} partition={} offset={} group=report-resource-events-v1",
                record.topic(), record.partition(), record.offset());
        try {
            event = json.readValue(record.value(), IntegrationEvent.class);
            receiver.receive(event);
            log.info("Kafka event handled: topic={} partition={} offset={} group=report-resource-events-v1 event_id={} type={} project_id={}",
                    record.topic(), record.partition(), record.offset(), event.eventId(), event.type(), event.projectId());
        } catch (JsonProcessingException malformed) {
            log.warn("Invalid Kafka event JSON: topic={} partition={} offset={} group=report-resource-events-v1",
                    record.topic(), record.partition(), record.offset());
            throw new IllegalArgumentException("Invalid event JSON", malformed);
        } catch (RuntimeException error) {
            log.warn("Kafka event handling failed: topic={} partition={} offset={} group=report-resource-events-v1 event_id={} error_type={}",
                    record.topic(), record.partition(), record.offset(), event == null ? null : event.eventId(), error.getClass().getSimpleName());
            throw error;
        }
    }
}
