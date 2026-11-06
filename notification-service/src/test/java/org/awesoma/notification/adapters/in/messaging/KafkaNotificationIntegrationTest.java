package org.awesoma.notification.adapters.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.awesoma.integration.IntegrationEvent;
import org.awesoma.notification.domain.Channel;
import org.awesoma.notification.domain.ChannelType;
import org.awesoma.notification.adapters.out.persistence.repository.ChannelRepository;
import org.awesoma.notification.adapters.out.persistence.repository.NotificationRepository;
import org.awesoma.notification.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.test.context.TestPropertySource;

/** Real Kafka listeners with PostgreSQL: duplicate incidents and project deletion. */
@EmbeddedKafka(kraft = true, partitions = 1, topics = {"incidents.events.v1", "resources.events.v1",
        "incidents.events.v1.dlt", "resources.events.v1.dlt"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = {"integration.incident-events.transport=kafka", "integration.resource-events.transport=kafka"})
class KafkaNotificationIntegrationTest extends AbstractIntegrationTest {
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper json;
    @Autowired ChannelRepository channels;
    @Autowired NotificationRepository notifications;
    @Autowired EmbeddedKafkaBroker broker;

    @Test
    void duplicateIncidentEventIsRecordedOnceAndProjectDeletionIsProcessed() throws Exception {
        long projectId = 184501L;
        long incidentId = 184502L;
        Channel channel = new Channel();
        channel.setProjectId(projectId);
        channel.setType(ChannelType.EMAIL);
        channel.setTarget("kafka-integration@example.com");
        channels.saveAndFlush(channel);
        var event = new IntegrationEvent(UUID.randomUUID(), 1, IntegrationEvent.INCIDENT_CHANGED,
                OffsetDateTime.now(), null, projectId, incidentId, "OPENED", null, null);
        String payload = json.writeValueAsString(event);
        kafka.send("incidents.events.v1", Long.toString(incidentId), payload).get();
        kafka.send("incidents.events.v1", Long.toString(incidentId), payload).get();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(
                notifications.findAll().stream().filter(n -> n.getIncidentId().equals(incidentId))).hasSize(1));
        var deleted = new IntegrationEvent(UUID.randomUUID(), 1, IntegrationEvent.PROJECT_DELETED,
                OffsetDateTime.now(), null, projectId, null, null, List.of(), null);
        kafka.send("resources.events.v1", Long.toString(projectId), json.writeValueAsString(deleted)).get();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(
                channels.findById(channel.getId())).isEmpty());
    }

    @Test
    void malformedMessagesAndJsonNullGoToDeadLetterTopic() throws Exception {
        var properties = KafkaTestUtils.consumerProps(UUID.randomUUID().toString(), "false", broker);
        var factory = new DefaultKafkaConsumerFactory<String, String>(properties,
                new StringDeserializer(), new StringDeserializer());
        try (var consumer = factory.createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "incidents.events.v1.dlt");
            kafka.send("incidents.events.v1", "bad-json", "{bad").get();
            assertThat(KafkaTestUtils.getSingleRecord(consumer, "incidents.events.v1.dlt", Duration.ofSeconds(30)).value())
                    .isEqualTo("{bad");
            kafka.send("incidents.events.v1", "null-json", "null").get();
            assertThat(KafkaTestUtils.getSingleRecord(consumer, "incidents.events.v1.dlt", Duration.ofSeconds(30)).value())
                    .isEqualTo("null");
        }
    }
}
