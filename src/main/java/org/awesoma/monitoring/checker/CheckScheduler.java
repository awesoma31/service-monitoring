package org.awesoma.monitoring.checker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.monitoring.domain.model.MonitorTarget;
import org.awesoma.monitoring.service.MonitorCheckService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the probing loop: asks which monitors are due, probes them, hands each result
 * back to be recorded.
 *
 * <p>Deliberately plain — a single scheduled method, no thread pool of its own and no
 * persistence. In lab 2 this package becomes a reactive service, and the only thing that
 * changes for the rest of the application is where the results come from.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "checker.enabled", havingValue = "true", matchIfMissing = true)
public class CheckScheduler {

    private final MonitorCheckService monitorCheckService;
    private final MonitorProbe probe;

    @Value("${checker.batch-size}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${checker.interval-ms}")
    public void runDueChecks() {
        for (MonitorTarget target : monitorCheckService.findDueTargets(batchSize)) {
            probeAndRecord(target);
        }
    }

    /** One failing monitor must not stop the rest of the batch from being checked. */
    private void probeAndRecord(MonitorTarget target) {
        try {
            monitorCheckService.record(target.monitorId(), probe.probe(target));
        } catch (RuntimeException exception) {
            log.warn("Failed to record a check for monitor {}", target.monitorId(), exception);
        }
    }
}
