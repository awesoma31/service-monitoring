package org.awesoma.configupdates;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.awesoma.integration.config.ConfigChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;

/**
 * Reads the compacted topic from the start each time, so an instance that was down still sees the
 * latest snapshot. The group includes the host name, so every replica gets every record.
 */
class ConfigUpdateListener implements ConsumerSeekAware {
    private static final Logger log = LoggerFactory.getLogger(ConfigUpdateListener.class);

    private final ConfigUpdateCoordinator coordinator;
    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    ConfigUpdateListener(ConfigUpdateCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        assignments.keySet().forEach(partition -> callback.seekToBeginning(partition.topic(), partition.partition()));
    }

    @KafkaListener(topics = ConfigChanged.TOPIC, groupId = "config-updates-${spring.application.name}-${HOSTNAME:local}")
    public void receive(ConsumerRecord<String, String> record) {
        try {
            ConfigChanged snapshot = json.readValue(record.value(), ConfigChanged.class);
            snapshot.validate();
            coordinator.accept(snapshot);
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException invalid) {
            // Nothing to retry, and a later valid snapshot replaces this one.
            log.warn("Invalid configuration snapshot skipped: partition={} offset={}",
                    record.partition(), record.offset());
        }
    }
}
