package org.awesoma.check.service;

import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.check.client.ReactiveMonitorClient;
import org.awesoma.check.domain.CheckResult;
import org.awesoma.check.domain.MonitorTarget;
import org.awesoma.check.domain.ProbeOutcome;
import org.awesoma.check.probe.MonitorProbe;
import org.awesoma.check.repository.CheckResultRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * One pass over the monitors that are due: probe each, store the result, report it.
 *
 * <p>The result is stored before it is reported. If monitor-service cannot be reached, the
 * history is still complete, and the monitor's state catches up with the next probe.
 */
@Slf4j
@Service
public class CheckRunner {

    private static final int MAX_MONITOR_TIMEOUT_MS = 60_000;
    private static final int COMPLETION_GRACE_PER_WAVE_MS = 30_000;

    private final ReactiveMonitorClient monitors;
    private final MonitorProbe probe;
    private final CheckResultRepository results;
    private final int batchSize;
    private final int concurrency;

    public CheckRunner(
            ReactiveMonitorClient monitors,
            MonitorProbe probe,
            CheckResultRepository results,
            @Value("${checker.batch-size}") int batchSize,
            @Value("${checker.concurrency}") int concurrency) {
        this.monitors = monitors;
        this.probe = probe;
        this.results = results;
        this.batchSize = batchSize;
        this.concurrency = concurrency;
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("checker.batch-size must be between 1 and 500");
        }
        if (concurrency < 1) {
            throw new IllegalArgumentException("checker.concurrency must be positive");
        }
    }

    public Mono<Void> runOnce() {
        return monitors.due(batchSize, leaseDurationMs())
                .flatMap(target -> check(target).onErrorResume(failure -> {
                    // One failing monitor must not stop the rest of the batch.
                    log.warn("Failed to check monitor {}", target.monitorId(), failure);
                    return Mono.empty();
                }), concurrency)
                .then();
    }

    private Mono<Void> check(MonitorTarget target) {
        return probe.probe(target).flatMap(outcome -> {
            ProbeOutcome claimedOutcome = outcome.forClaim(target.claimToken());
            return results
                    .save(CheckResult.of(target.monitorId(), outcome, OffsetDateTime.now()))
                    .then(monitors.report(target.monitorId(), claimedOutcome));
        });
    }

    /** Covers every sequential wave in a batch at the largest allowed per-monitor timeout. */
    long leaseDurationMs() {
        long waves = (batchSize + (long) concurrency - 1) / concurrency;
        return waves * (MAX_MONITOR_TIMEOUT_MS + COMPLETION_GRACE_PER_WAVE_MS);
    }
}
