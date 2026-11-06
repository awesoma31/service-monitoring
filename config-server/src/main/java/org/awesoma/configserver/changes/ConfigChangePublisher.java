package org.awesoma.configserver.changes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.awesoma.configserver.changes.ConfigRepositoryScanner.Target;
import org.awesoma.integration.config.ConfigChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Publishes a service's snapshot when its files change. Polling rather than file events, since
 * those do not reliably cross a Docker bind mount. No state survives a restart: everything is
 * published once on start, and a receiver ignores a snapshot equal to what it runs with. A failed
 * publication is simply retried by the next poll, so nothing is lost while Kafka is away. While a
 * file is invalid, nothing is published and services keep their settings.
 */
public class ConfigChangePublisher {
    private static final Logger log = LoggerFactory.getLogger(ConfigChangePublisher.class);

    private final ConfigRepositoryScanner scanner;
    private final ConfigSnapshots snapshots;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, String> published = new HashMap<>();
    private String publishedFingerprint;
    private long lastRevision;

    ConfigChangePublisher(ConfigRepositoryScanner scanner, ConfigSnapshots snapshots,
            KafkaTemplate<String, String> kafka, Clock clock) {
        this.scanner = scanner;
        this.snapshots = snapshots;
        this.kafka = kafka;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${config.changes.poll-interval:PT5S}")
    public void publishChanges() {
        ConfigRepositoryScanner.Scan scan;
        try {
            scan = scanner.scan();
        } catch (IOException | RuntimeException refused) {
            log.warn("Configuration repository refused, services keep their settings: {}", refused.getMessage());
            return;
        }
        if (scan.fingerprint().equals(publishedFingerprint)) return;
        boolean complete = true;
        for (Target target : scan.targets()) {
            try {
                publish(target);
            } catch (RuntimeException | JsonProcessingException | ExecutionException | TimeoutException failure) {
                complete = false;
                log.warn("Configuration snapshot not published, will retry: service={} profile={} error={}",
                        target.service(), target.profile(), failure.getMessage());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (complete) publishedFingerprint = scan.fingerprint();
    }

    private void publish(Target target)
            throws JsonProcessingException, ExecutionException, TimeoutException, InterruptedException {
        lastRevision = Math.max(clock.millis(), lastRevision + 1);
        ConfigChanged snapshot = snapshots.read(target, lastRevision);
        snapshot.validate();
        if (snapshot.contentHash().equals(published.get(snapshot.key()))) return;
        var sent = kafka.send(ConfigChanged.TOPIC, snapshot.key(), json.writeValueAsString(snapshot))
                .get(10, TimeUnit.SECONDS);
        published.put(snapshot.key(), snapshot.contentHash());
        log.info("Configuration snapshot published: service={} profile={} revision={} partition={} offset={}",
                snapshot.service(), snapshot.profile(), snapshot.revision(),
                sent.getRecordMetadata().partition(), sent.getRecordMetadata().offset());
    }
}
