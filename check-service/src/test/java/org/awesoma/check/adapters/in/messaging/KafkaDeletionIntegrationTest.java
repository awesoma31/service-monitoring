package org.awesoma.check.adapters.in.messaging;

import org.awesoma.check.adapters.out.messaging.CheckOutboxDispatcher;
import org.awesoma.check.adapters.out.messaging.CheckResultPublisher;
import org.awesoma.check.application.port.CheckResultRecorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.awesoma.check.domain.CheckResult;
import org.awesoma.check.domain.ProbeOutcome;
import org.awesoma.check.adapters.out.persistence.repository.CheckResultRepository;
import org.awesoma.check.support.AbstractIntegrationTest;
import org.awesoma.integration.IntegrationEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.reactive.TransactionalOperator;

@EmbeddedKafka(kraft = true, partitions = 1, topics = {"resources.events.v1", "resources.events.v1.dlt", "checks.completed.v1"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = {"integration.resource-events.transport=kafka", "integration.check-results.transport=kafka", "integration.outbox.enabled=false"})
class KafkaDeletionIntegrationTest extends AbstractIntegrationTest {
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper json;
    @Autowired CheckResultRepository results;
    @Autowired DatabaseClient db;
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired CheckResultRecorder recorder;
    @Autowired CheckResultPublisher publisher;
    @Autowired TransactionalOperator transaction;

    @Test
    void projectDeletionRemovesHistoryAndFencesTheMonitor() throws Exception {
        long monitorId = 884201L;
        results.save(CheckResult.of(monitorId, ProbeOutcome.success(5, 200), OffsetDateTime.now())).block();
        var event = new IntegrationEvent(UUID.randomUUID(), 1, IntegrationEvent.PROJECT_DELETED,
                OffsetDateTime.now(), null, 884202L, null, null, List.of(monitorId), null);
        String payload = json.writeValueAsString(event);
        kafka.send("resources.events.v1", "884202", payload).get();
        kafka.send("resources.events.v1", "884202", payload).get();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            long deleted = db.sql("SELECT count(*) FROM deleted_monitors WHERE monitor_id = :id")
                    .bind("id", monitorId).map((r, m) -> r.get(0, Long.class)).one().block();
            assertThat(deleted).isEqualTo(1);
            assertThat(results.findPage(monitorId, 10, 0).collectList().block()).isEmpty();
        });
    }

    @Test
    void outboxDispatcherPublishesThePersistedResultToKafka() throws Exception {
        db.sql("DELETE FROM integration_outbox").fetch().rowsUpdated().block();
        long monitorId = 884203L;
        UUID token = UUID.randomUUID();
        var options = KafkaTestUtils.consumerProps(UUID.randomUUID().toString(), "false", broker);
        var factory = new DefaultKafkaConsumerFactory<String, String>(options,
                new StringDeserializer(), new StringDeserializer());
        try (var consumer = factory.createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "checks.completed.v1");
            recorder.record(monitorId, ProbeOutcome.success(7, 200).forClaim(token), OffsetDateTime.now()).block();
            new CheckOutboxDispatcher(db, json, transaction, publisher).dispatch();
            var message = KafkaTestUtils.getSingleRecord(consumer, "checks.completed.v1", Duration.ofSeconds(30));
            IntegrationEvent event = json.readValue(message.value(), IntegrationEvent.class);
            assertThat(event.monitorId()).isEqualTo(monitorId);
            assertThat(event.outcome().claimToken()).isEqualTo(token);
            assertThat(db.sql("SELECT count(*) FROM integration_outbox").map((r, m) -> r.get(0, Long.class)).one().block()).isZero();
        }
    }
}
