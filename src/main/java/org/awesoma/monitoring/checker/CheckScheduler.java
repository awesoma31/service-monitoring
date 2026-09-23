package org.awesoma.monitoring.checker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.monitoring.domain.model.MonitorTarget;
import org.awesoma.monitoring.service.MonitorCheckService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    private void probeAndRecord(MonitorTarget target) {
        try {
            monitorCheckService.record(target.monitorId(), probe.probe(target));
        } catch (RuntimeException exception) {
            log.warn("Failed to record a check for monitor {}", target.monitorId(), exception);
        }
    }
}
