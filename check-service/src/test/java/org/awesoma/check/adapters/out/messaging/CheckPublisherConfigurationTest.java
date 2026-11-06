package org.awesoma.check.adapters.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.awesoma.check.adapters.out.http.ReactiveMonitorClient;
import org.awesoma.integration.IntegrationEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class CheckPublisherConfigurationTest {
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final ReactiveMonitorClient monitor = mock(ReactiveMonitorClient.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(CheckPublisherConfiguration.class)
            .withBean(ReactiveMonitorClient.class, () -> monitor)
            .withBean(ObjectMapper.class, () -> json)
            .withBean(KafkaTemplate.class, () -> kafka);

    @Test
    void choosesExactlyOneAdapterFromConfiguration() {
        context.run(app -> assertThat(app.getBean(CheckResultPublisher.class).transport()).isEqualTo("http"));
        context.withPropertyValues("integration.check-results.transport=kafka")
                .run(app -> assertThat(app.getBean(CheckResultPublisher.class).transport()).isEqualTo("kafka"));
        context.withPropertyValues("integration.check-results.transport=typo")
                .run(app -> assertThat(app.getBeansOfType(CheckResultPublisher.class)).isEmpty());
    }

    @Test
    void producerSendDoesNotBlockTheReactiveEventLoop() {
        var invokedOn = new AtomicReference<String>();
        when(kafka.send(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            invokedOn.set(Thread.currentThread().getName());
            return CompletableFuture.completedFuture(mock(SendResult.class));
        });
        var event = new IntegrationEvent(UUID.randomUUID(), 1, IntegrationEvent.CHECK_COMPLETED,
                OffsetDateTime.now(), 1L, null, null, null, null,
                new IntegrationEvent.CheckOutcome("SUCCESS", 1, 200, null, UUID.randomUUID()));
        CheckResultPublisher publisher = new CheckPublisherConfiguration().kafkaCheckResults(kafka, json);
        StepVerifier.create(Mono.defer(() -> publisher.publish(event)).subscribeOn(Schedulers.parallel()))
                .verifyComplete();
        assertThat(invokedOn.get()).startsWith("boundedElastic-");
    }
}
