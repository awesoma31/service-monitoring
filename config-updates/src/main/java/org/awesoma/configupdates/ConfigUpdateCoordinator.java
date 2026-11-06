package org.awesoma.configupdates;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.awesoma.integration.config.ConfigChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Compares the latest snapshot with what this instance got from the config server when it started.
 * That comparison is the only state, so a restart cannot repeat: the new instance starts with the
 * new values, finds no difference and does nothing. An older record read from the topic after a
 * restart is harmless too, since the decision waits for the settle delay and takes the latest.
 *
 * <p>Environment variables and system properties stay above the snapshot, which sits right above
 * the values from the config server.
 */
final class ConfigUpdateCoordinator implements AutoCloseable {
    static final String SNAPSHOT_SOURCE = "kafkaConfigSnapshot";
    private static final String SERVER_SOURCE_PREFIX = "configserver:";
    private static final Logger log = LoggerFactory.getLogger(ConfigUpdateCoordinator.class);

    private final ConfigurableEnvironment environment;
    private final ApplicationEventPublisher events;
    private final Restarter restarter;
    private final ScheduledExecutorService executor;
    private final String service;
    private final List<String> hotPrefixes;
    private final Duration settleDelay;
    private final Duration restartDelay;

    private ConfigChanged latest;
    private ScheduledFuture<?> pendingDecision;
    private ScheduledFuture<?> pendingRestart;
    private Map<String, Object> applied = Map.of();

    ConfigUpdateCoordinator(ConfigurableEnvironment environment, ApplicationEventPublisher events, Restarter restarter,
            ScheduledExecutorService executor, String service, List<String> hotPrefixes, Duration settleDelay,
            Duration restartDelay) {
        this.environment = environment;
        this.events = events;
        this.restarter = restarter;
        this.executor = executor;
        this.service = service;
        this.hotPrefixes = hotPrefixes;
        this.settleDelay = settleDelay;
        this.restartDelay = restartDelay;
    }

    synchronized void accept(ConfigChanged snapshot) {
        if (!service.equals(snapshot.service()) || !profile().equals(snapshot.profile())) return;
        if (latest != null && snapshot.revision() <= latest.revision()) return;
        latest = snapshot;
        if (pendingDecision != null) pendingDecision.cancel(false);
        pendingDecision = executor.schedule(this::decide, settleDelay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** A snapshot exists for one profile at a time; several active profiles are not supported. */
    private String profile() {
        String[] active = environment.getActiveProfiles();
        return active.length == 0 ? ConfigChanged.DEFAULT_PROFILE : String.join(",", Arrays.asList(active));
    }

    synchronized void decide() {
        Map<String, String> started = valuesFromConfigServer();
        if (started.isEmpty()) {
            log.warn("Configuration snapshot ignored: this instance did not start from the config server");
            return;
        }
        Map<String, String> wanted = latest.properties();
        Map<String, Object> hot = new TreeMap<>();
        Set<String> needRestart = new TreeSet<>();
        wanted.forEach((key, value) -> {
            if (value.equals(started.get(key))) return;
            if (isHot(key)) hot.put(key, value);
            else needRestart.add(key);
        });
        started.keySet().stream().filter(key -> !wanted.containsKey(key)).forEach(needRestart::add);
        // An overlaid value that is gone has nothing to fall back to without a restart.
        applied.keySet().stream().filter(key -> !hot.containsKey(key) && !started.containsKey(key))
                .forEach(needRestart::add);

        applyHot(hot);
        scheduleRestart(needRestart);
    }

    private boolean isHot(String key) {
        return hotPrefixes.stream().anyMatch(key::startsWith);
    }

    private void applyHot(Map<String, Object> hot) {
        if (hot.equals(applied)) return;
        Set<String> changed = new TreeSet<>(hot.keySet());
        changed.addAll(applied.keySet());
        changed.removeIf(key -> Objects.equals(hot.get(key), applied.get(key)));
        var sources = environment.getPropertySources();
        sources.remove(SNAPSHOT_SOURCE);
        if (!hot.isEmpty()) {
            sources.addBefore(firstServerSource(), new MapPropertySource(SNAPSHOT_SOURCE, hot));
        }
        applied = hot;
        events.publishEvent(new EnvironmentChangeEvent(changed));
        log.info("Configuration applied without a restart: service={} revision={} keys={}",
                service, latest.revision(), changed);
    }

    private void scheduleRestart(Set<String> needRestart) {
        if (needRestart.isEmpty()) {
            if (pendingRestart != null) {
                pendingRestart.cancel(false);
                pendingRestart = null;
                log.info("Planned restart cancelled, the settings are back: service={}", service);
            }
            return;
        }
        if (pendingRestart != null) return;
        log.info("Restart planned for new configuration: service={} revision={} delay={} keys={}",
                service, latest.revision(), restartDelay, needRestart);
        pendingRestart = executor.schedule(restarter::restart, restartDelay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private Map<String, String> valuesFromConfigServer() {
        Map<String, String> values = new TreeMap<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source.getName().startsWith(SERVER_SOURCE_PREFIX) && source instanceof EnumerablePropertySource<?> e) {
                for (String key : e.getPropertyNames()) {
                    if (!ConfigChanged.isSecret(key)) values.putIfAbsent(key, String.valueOf(e.getProperty(key)));
                }
            }
        }
        return values;
    }

    private String firstServerSource() {
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source.getName().startsWith(SERVER_SOURCE_PREFIX)) return source.getName();
        }
        throw new IllegalStateException("No config server source");
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
