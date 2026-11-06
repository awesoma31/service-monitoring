package org.awesoma.check.config;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Poison messages go to DLT; transient failures retain their offset and retry. */
@Configuration(proxyBeanMethods = false)
@Slf4j
@ConditionalOnExpression("'${integration.resource-events.transport:http}' == 'kafka'")
class KafkaConsumerErrors {
    @Bean
    DefaultErrorHandler integrationKafkaErrorHandler(KafkaTemplate<String, String> kafka) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, error) -> new TopicPartition(record.topic() + ".dlt", record.partition())) {
            @Override
            public void accept(ConsumerRecord<?, ?> record, Consumer<?, ?> consumer, Exception error) {
                try {
                    super.accept(record, consumer, error);
                    log.info("Kafka event sent to DLT: source_topic={} partition={} offset={} dlt_topic={}",
                            record.topic(), record.partition(), record.offset(), record.topic() + ".dlt");
                } catch (RuntimeException failure) {
                    log.warn("Kafka DLT publication failed: source_topic={} partition={} offset={} dlt_topic={} error_type={}",
                            record.topic(), record.partition(), record.offset(), record.topic() + ".dlt", failure.getClass().getSimpleName());
                    throw failure;
                }
            }
        };
        recoverer.setFailIfSendResultIsError(true);
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000, Long.MAX_VALUE));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
