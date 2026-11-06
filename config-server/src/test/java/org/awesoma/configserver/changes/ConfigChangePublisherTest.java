package org.awesoma.configserver.changes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.awesoma.integration.config.ConfigChanged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class ConfigChangePublisherTest {
    @TempDir Path root;

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final EnvironmentRepository environments = mock(EnvironmentRepository.class);
    private String limit = "50";
    private ConfigChangePublisher publisher;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(root.resolve("application.yml"), "shared: 1\n");
        Files.writeString(root.resolve("application-dev.yml"), "shared: 2\n");
        Files.createDirectory(root.resolve("report-service"));
        Files.writeString(root.resolve("report-service/application.yml"), "report: 1\n");
        when(environments.findOne(anyString(), anyString(), any())).thenAnswer(call -> {
            var environment = new Environment(call.getArgument(0), (String) call.getArgument(1));
            environment.add(new PropertySource("service", Map.of("report.limit", limit, "db.password", "hidden")));
            environment.add(new PropertySource("shared", Map.of("report.limit", "1", "shared", "1")));
            return environment;
        });
        var metadata = new RecordMetadata(new TopicPartition(ConfigChanged.TOPIC, 0), 0, 0, 0, 0, 0);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                new SendResult<>(new ProducerRecord<>(ConfigChanged.TOPIC, "k", "v"), metadata)));
        publisher = new ConfigChangePublisher(new ConfigRepositoryScanner(root), new ConfigSnapshots(environments),
                kafka, Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC));
    }

    @Test
    void publishesEveryProfileOfAServiceOnStart() {
        publisher.publishChanges();

        verify(kafka).send(eq(ConfigChanged.TOPIC), eq("report-service/default"), anyString());
        verify(kafka).send(eq(ConfigChanged.TOPIC), eq("report-service/dev"), anyString());
    }

    @Test
    void snapshotKeepsTheServiceValueAndLeavesOutSecrets() {
        publisher.publishChanges();

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafka, times(2)).send(anyString(), anyString(), captor.capture());
        assertThat(captor.getValue()).contains("\"report.limit\":\"50\"").doesNotContain("password");
    }

    @Test
    void publishesAgainOnlyWhatChanged() throws IOException {
        publisher.publishChanges();
        clearInvocations(kafka);

        publisher.publishChanges();
        verify(kafka, never()).send(anyString(), anyString(), anyString());

        Files.writeString(root.resolve("report-service/application.yml"), "report: 2\n");
        limit = "60";
        publisher.publishChanges();
        verify(kafka, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void anUntouchedSnapshotIsNotPublishedAfterAnEditThatChangesNothing() throws IOException {
        publisher.publishChanges();
        clearInvocations(kafka);

        Files.writeString(root.resolve("report-service/application.yml"), "# a comment\nreport: 1\n");
        publisher.publishChanges();

        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void invalidYamlPublishesNothingAndKeepsTheLastValues() throws IOException {
        publisher.publishChanges();
        clearInvocations(kafka);

        Files.writeString(root.resolve("report-service/application.yml"), "report: [broken\n");
        limit = "60";
        publisher.publishChanges();

        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void aFailedPublicationIsRetriedByTheNextPoll() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("no brokers")));
        publisher.publishChanges();
        clearInvocations(kafka);

        publisher.publishChanges();

        verify(kafka, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void scannerSkipsFilesThatAreNotConfiguration() throws IOException {
        Files.writeString(root.resolve("report-service/.application.yml.swp"), "binary: [");
        Files.writeString(root.resolve("report-service/notes.txt"), "text");

        var targets = new ConfigRepositoryScanner(root).scan().targets();

        assertThat(targets).isEqualTo(List.of(
                new ConfigRepositoryScanner.Target("report-service", "default"),
                new ConfigRepositoryScanner.Target("report-service", "dev")));
    }
}
