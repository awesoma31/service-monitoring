package org.awesoma.check.application.usecase;

import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.check.application.port.MonitorDirectory;
import org.awesoma.check.domain.MonitorTarget;
import org.awesoma.check.domain.ProbeOutcome;
import org.awesoma.check.application.port.MonitorProbe;
import org.awesoma.check.application.port.CheckResultRecorder;
import reactor.core.publisher.Mono;

/**
 * One pass over due monitors: probe each and atomically store its history and outbox event.
 * The outbox dispatcher retries reporting independently of this scheduler.
 */
@Slf4j
public class CheckRunner {

    private final MonitorDirectory monitors;
    private final MonitorProbe probe;
    private final CheckResultRecorder results;
    private final int batchSize;
    private final int concurrency;

    public CheckRunner(
            MonitorDirectory monitors,
            MonitorProbe probe,
            CheckResultRecorder results,
            int batchSize,
            int concurrency) {
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
        return monitors.due(batchSize, concurrency)
                .flatMap(target -> check(target).onErrorResume(failure -> {
                    // One failing monitor must not stop the rest of the batch.
                    log.error("Monitor check pipeline failed: monitor_id={}", target.monitorId(), failure);
                    return Mono.empty();
                }), concurrency)
                .then();
    }

    private Mono<Void> check(MonitorTarget target) {
        return probe.probe(target).flatMap(outcome -> {
            ProbeOutcome claimedOutcome = outcome.forClaim(target.claimToken());
            return results.record(target.monitorId(), claimedOutcome, OffsetDateTime.now());
        });
    }
}
