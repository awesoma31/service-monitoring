package org.awesoma.configupdates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.awesoma.integration.config.ConfigChanged;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class ConfigUpdateCoordinatorTest {
    private static final Map<String, String> STARTED = Map.of(
            "logging.level.org.awesoma", "INFO", "report.limit", "${LIMIT:50}");

    private final MockEnvironment environment = new MockEnvironment();
    private final List<EnvironmentChangeEvent> events = new ArrayList<>();
    private final AtomicInteger restarts = new AtomicInteger();
    private ConfigUpdateCoordinator coordinator;

    @BeforeEach
    void setUp() {
        environment.getPropertySources().addLast(new MapPropertySource("configserver:report", new HashMap<>(STARTED)));
        ApplicationEventPublisher publisher = event -> events.add((EnvironmentChangeEvent) event);
        coordinator = new ConfigUpdateCoordinator(environment, publisher, restarts::incrementAndGet,
                Executors.newSingleThreadScheduledExecutor(), "report-service", List.of("logging.level."),
                Duration.ofHours(1), Duration.ZERO);
    }

    @AfterEach
    void tearDown() {
        coordinator.close();
    }

    private static ConfigChanged snapshot(long revision, Map<String, String> properties) {
        return new ConfigChanged(1, "report-service", "default", revision, "sha256:x", properties);
    }

    private static Map<String, String> with(String key, String value) {
        var properties = new HashMap<>(STARTED);
        properties.put(key, value);
        return properties;
    }

    private void waitForRestart() throws InterruptedException {
        for (int i = 0; i < 100 && restarts.get() == 0; i++) Thread.sleep(20);
    }

    @Test
    void anUnchangedSnapshotDoesNothing() throws InterruptedException {
        coordinator.accept(snapshot(1, STARTED));
        coordinator.decide();

        Thread.sleep(100);
        assertThat(restarts).hasValue(0);
        assertThat(events).isEmpty();
    }

    @Test
    void aLogLevelAppliesWithoutARestart() throws InterruptedException {
        coordinator.accept(snapshot(1, with("logging.level.org.awesoma", "DEBUG")));
        coordinator.decide();

        Thread.sleep(100);
        assertThat(environment.getProperty("logging.level.org.awesoma")).isEqualTo("DEBUG");
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getKeys()).containsExactly("logging.level.org.awesoma");
        assertThat(restarts).hasValue(0);
    }

    @Test
    void anEnvironmentVariableStaysAboveTheSnapshot() {
        environment.setProperty("logging.level.org.awesoma", "WARN");

        coordinator.accept(snapshot(1, with("logging.level.org.awesoma", "DEBUG")));
        coordinator.decide();

        assertThat(environment.getProperty("logging.level.org.awesoma")).isEqualTo("WARN");
    }

    @Test
    void revertingALogLevelRemovesTheOverlay() {
        coordinator.accept(snapshot(1, with("logging.level.org.awesoma", "DEBUG")));
        coordinator.decide();
        coordinator.accept(snapshot(2, STARTED));
        coordinator.decide();

        assertThat(environment.getProperty("logging.level.org.awesoma")).isEqualTo("INFO");
        assertThat(environment.getPropertySources().contains(ConfigUpdateCoordinator.SNAPSHOT_SOURCE)).isFalse();
    }

    @Test
    void anyOtherChangeRestarts() throws InterruptedException {
        coordinator.accept(snapshot(1, with("report.limit", "60")));
        coordinator.decide();

        waitForRestart();
        assertThat(restarts).hasValue(1);
    }

    @Test
    void aRemovedKeyRestarts() throws InterruptedException {
        coordinator.accept(snapshot(1, Map.of("logging.level.org.awesoma", "INFO")));
        coordinator.decide();

        waitForRestart();
        assertThat(restarts).hasValue(1);
    }

    @Test
    void anOlderRevisionIsIgnored() throws InterruptedException {
        coordinator.accept(snapshot(5, STARTED));
        coordinator.accept(snapshot(4, with("report.limit", "60")));
        coordinator.decide();

        Thread.sleep(100);
        assertThat(restarts).hasValue(0);
    }

    @Test
    void anotherServiceOrProfileIsIgnored() throws InterruptedException {
        coordinator.accept(new ConfigChanged(1, "gateway", "default", 1, "h", with("report.limit", "60")));
        coordinator.accept(new ConfigChanged(1, "report-service", "dev", 2, "h", with("report.limit", "60")));
        coordinator.accept(snapshot(1, STARTED));
        coordinator.decide();

        Thread.sleep(100);
        assertThat(restarts).hasValue(0);
    }

    @Test
    void aSnapshotEqualToTheNewStartPlansNoRestartAgain() throws InterruptedException {
        var afterRestart = new HashMap<>(STARTED);
        afterRestart.put("report.limit", "60");
        environment.getPropertySources().replace("configserver:report",
                new MapPropertySource("configserver:report", new HashMap<>(afterRestart)));

        coordinator.accept(snapshot(1, afterRestart));
        coordinator.decide();

        Thread.sleep(100);
        assertThat(restarts).hasValue(0);
    }

    @Test
    void aPlannedRestartIsCancelledWhenTheFileIsReverted() throws InterruptedException {
        var slow = new ConfigUpdateCoordinator(environment, mock(ApplicationEventPublisher.class),
                restarts::incrementAndGet, Executors.newSingleThreadScheduledExecutor(), "report-service",
                List.of(), Duration.ofHours(1), Duration.ofMillis(200));
        slow.accept(snapshot(1, with("report.limit", "60")));
        slow.decide();
        slow.accept(snapshot(2, STARTED));
        slow.decide();

        Thread.sleep(400);
        assertThat(restarts).hasValue(0);
        slow.close();
    }

    @Test
    void anInstanceThatDidNotStartFromTheConfigServerIgnoresSnapshots() throws InterruptedException {
        environment.getPropertySources().remove("configserver:report");

        coordinator.accept(snapshot(1, with("report.limit", "60")));
        coordinator.decide();

        Thread.sleep(100);
        assertThat(restarts).hasValue(0);
    }

    @Test
    void listenerSkipsAnInvalidRecordAndPassesAValidOne() {
        var listener = new ConfigUpdateListener(coordinator);

        listener.receive(new ConsumerRecord<>(ConfigChanged.TOPIC, 0, 0, "k", "not json"));
        listener.receive(new ConsumerRecord<>(ConfigChanged.TOPIC, 0, 1, "k", "{\"schemaVersion\":2}"));
        listener.receive(new ConsumerRecord<>(ConfigChanged.TOPIC, 0, 2, "k", """
                {"schemaVersion":1,"service":"report-service","profile":"default","revision":7,
                 "contentHash":"h","properties":{"logging.level.org.awesoma":"DEBUG","report.limit":"${LIMIT:50}"}}
                """));
        coordinator.decide();

        assertThat(environment.getProperty("logging.level.org.awesoma")).isEqualTo("DEBUG");
    }
}
