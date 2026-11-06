package org.awesoma.report.adapters.in.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awesoma.integration.IntegrationEvent;
import org.awesoma.report.application.port.ReportRepository;
import org.awesoma.report.domain.GenerationParameters;
import org.awesoma.report.domain.MonitorSelection;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportFormat;
import org.awesoma.report.domain.ReportPeriod;
import org.awesoma.report.domain.ReportStatus;
import org.awesoma.report.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

@EmbeddedKafka(kraft = true, partitions = 1, topics = {"resources.events.v1", "resources.events.v1.dlt"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = "integration.resource-events.transport=kafka")
class KafkaProjectDeletionIntegrationTest extends AbstractIntegrationTest {
    @Autowired private KafkaTemplate<String, String> kafka;
    @Autowired private ObjectMapper json;
    @Autowired private ReportRepository reports;
    @Autowired private JdbcTemplate db;
    @Autowired private EmbeddedKafkaBroker broker;

    @Test
    void consumesProjectDeletedOnceInItsOwnGroup() throws Exception {
        long project = 884_301;
        var job = reports.save(Report.queueGeneration(UUID.randomUUID(), project, 7, ReportFormat.CSV,
                new GenerationParameters(new ReportPeriod(Instant.parse("2026-10-01T00:00:00Z"),
                        Instant.parse("2026-10-02T00:00:00Z")), MonitorSelection.allMonitors(),
                        Instant.parse("2026-11-01T00:00:00Z"))));
        var event = new IntegrationEvent(UUID.randomUUID(), 1, IntegrationEvent.PROJECT_DELETED,
                OffsetDateTime.now(), null, project, null, null, List.of(), null);
        String payload = json.writeValueAsString(event);

        kafka.send("resources.events.v1", "project-" + project, payload).get();
        kafka.send("resources.events.v1", "project-" + project, payload).get();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(reports.findById(job.id()).orElseThrow().status()).isEqualTo(ReportStatus.DELETING);
            assertThat(db.queryForObject("SELECT count(*) FROM integration_inbox WHERE event_id = ?", Long.class,
                    event.eventId())).isEqualTo(1);
            assertThat(db.queryForObject("SELECT count(*) FROM deleted_projects WHERE project_id = ?", Long.class,
                    project)).isEqualTo(1);
        });
    }

    @Test
    void sendsAMalformedEventToTheDeadLetterTopic() throws Exception {
        var options = KafkaTestUtils.consumerProps(UUID.randomUUID().toString(), "false", broker);
        var factory = new DefaultKafkaConsumerFactory<String, String>(options, new StringDeserializer(),
                new StringDeserializer());
        try (var consumer = factory.createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "resources.events.v1.dlt");
            kafka.send("resources.events.v1", "bad", "{not json").get();

            var dead = KafkaTestUtils.getSingleRecord(consumer, "resources.events.v1.dlt", Duration.ofSeconds(30));
            assertThat(dead.value()).isEqualTo("{not json");
        }
    }
}
