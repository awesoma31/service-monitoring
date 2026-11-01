package org.awesoma.monitoring.integration.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.awesoma.integration.IntegrationEvent;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.awesoma.monitoring.repository.MonitorRepository;
import org.awesoma.monitoring.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@EmbeddedKafka(kraft = true, partitions = 1, topics = {"checks.completed.v1", "checks.completed.v1.dlt"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = {"integration.check-results.transport=kafka", "integration.outbox.enabled=false"})
class KafkaCheckResultIntegrationTest extends AbstractIntegrationTest {
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper json;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate tx;
    @Autowired MonitorRepository monitors;

    @Test
    void delayedCurrentClaimCreatesAnIncidentEvenIfDeliveredTwice() throws Exception {
        UUID token = UUID.randomUUID();
        Long id = tx.execute(status -> {
            Project project = new Project();
            project.setOwnerId(remoteUser("kafka-check@example.com"));
            project.setName("Kafka");
            project.setSlug("kafka-check-" + System.nanoTime());
            entityManager.persist(project);
            Monitor monitor = new Monitor();
            monitor.setProject(project);
            monitor.setName("Kafka monitor");
            monitor.setUrl("https://example.com");
            monitor.setIntervalSec(60);
            monitor.setTimeoutMs(5000);
            monitor.claimForCheck(token, OffsetDateTime.now().minusSeconds(1));
            entityManager.persist(monitor);
            return monitor.getId();
        });
        var outcome = new IntegrationEvent.CheckOutcome("CONNECTION_ERROR", 10, null, "unavailable", token);
        var event = new IntegrationEvent(UUID.randomUUID(), 1, IntegrationEvent.CHECK_COMPLETED,
                OffsetDateTime.now(), id, null, null, null, null, outcome);
        String payload = json.writeValueAsString(event);
        kafka.send("checks.completed.v1", id.toString(), payload).get();
        kafka.send("checks.completed.v1", id.toString(), payload).get();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(
                monitors.findById(id).orElseThrow().getCurrentState()).isEqualTo(MonitorState.DOWN));
    }
}
