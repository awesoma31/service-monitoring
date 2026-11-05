package org.awesoma.monitoring.integration.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.awesoma.integration.IntegrationEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "integration.check-results", name = "transport", havingValue = "kafka")
class MonitorKafkaListener {
    private final MonitorEventHandler handler;
    private final ObjectMapper json;

    @KafkaListener(topics = "checks.completed.v1", groupId = "monitor-check-results-v1")
    public void receive(ConsumerRecord<String, String> record) {
        IntegrationEvent event = null;
        log.debug("Kafka record received: topic={} partition={} offset={} group=monitor-check-results-v1",
                record.topic(), record.partition(), record.offset());
        try {
            event = json.readValue(record.value(), IntegrationEvent.class);
            handler.handle(event);
            log.info("Kafka event handled: topic={} partition={} offset={} group=monitor-check-results-v1 event_id={} type={} monitor_id={}",
                    record.topic(), record.partition(), record.offset(), event.eventId(), event.type(), event.monitorId());
        } catch (JsonProcessingException e) {
            log.warn("Invalid Kafka event JSON: topic={} partition={} offset={} group=monitor-check-results-v1",
                    record.topic(), record.partition(), record.offset());
            throw new IllegalArgumentException("Invalid integration event JSON", e);
        } catch (RuntimeException e) {
            log.warn("Kafka event handling failed: topic={} partition={} offset={} group=monitor-check-results-v1 event_id={} error_type={}",
                    record.topic(), record.partition(), record.offset(), event == null ? null : event.eventId(), e.getClass().getSimpleName());
            throw e;
        }
    }
}
