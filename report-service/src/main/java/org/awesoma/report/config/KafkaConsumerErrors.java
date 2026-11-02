package org.awesoma.report.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Poison messages go to the DLT; a temporary failure keeps the offset and is retried. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "integration.resource-events", name = "transport", havingValue = "kafka")
class KafkaConsumerErrors {

    @Bean
    DefaultErrorHandler integrationKafkaErrorHandler(KafkaTemplate<String, String> kafka) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, error) -> new TopicPartition(record.topic() + ".dlt", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000, Long.MAX_VALUE));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
