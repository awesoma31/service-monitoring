package org.awesoma.check.adapters.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.check.adapters.out.http.ReactiveMonitorClient;
import org.awesoma.integration.IntegrationEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@Slf4j
class CheckPublisherConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "integration.check-results", name = "transport", havingValue = "http", matchIfMissing = true)
    CheckResultPublisher httpCheckResults(ReactiveMonitorClient monitor) {
        return new CheckResultPublisher() {
            public String transport() { return "http"; }
            public Mono<Void> publish(IntegrationEvent event) { return monitor.reportEvent(event); }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "integration.check-results", name = "transport", havingValue = "kafka")
    CheckResultPublisher kafkaCheckResults(KafkaTemplate<String, String> kafka, ObjectMapper json) {
        return new CheckResultPublisher() {
            public String transport() { return "kafka"; }
            public Mono<Void> publish(IntegrationEvent event) {
                return Mono.defer(() -> {
                    try {
                        String payload = json.writeValueAsString(event);
                        // KafkaProducer.send can block while waiting for metadata or buffer space.
                        return Mono.fromCallable(() -> kafka.send("checks.completed.v1",
                                        event.monitorId().toString(), payload))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(Mono::fromFuture)
                                .timeout(Duration.ofSeconds(20))
                                .doOnNext(result -> {
                                    var metadata = result.getRecordMetadata();
                                    if (metadata != null) {
                                        log.info("Kafka event published: topic=checks.completed.v1 partition={} offset={} event_id={} type={} monitor_id={}",
                                                metadata.partition(), metadata.offset(), event.eventId(), event.type(), event.monitorId());
                                    }
                                })
                                .doOnError(error -> log.warn("Kafka event publication failed: topic=checks.completed.v1 event_id={} type={} monitor_id={} error_type={}",
                                        event.eventId(), event.type(), event.monitorId(), error.getClass().getSimpleName()))
                                .then();
                    } catch (JsonProcessingException e) {
                        log.warn("Kafka event serialization failed: topic=checks.completed.v1 event_id={} type={} monitor_id={} error_type={}",
                                event.eventId(), event.type(), event.monitorId(), e.getClass().getSimpleName());
                        return Mono.error(e);
                    }
                });
            }
        };
    }
}
