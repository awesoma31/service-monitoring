package org.awesoma.check.client;

import lombok.RequiredArgsConstructor;
import org.awesoma.check.domain.MonitorTarget;
import org.awesoma.check.domain.ProbeOutcome;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Feign blocks the calling thread, which must never be a Netty event-loop thread. Every call
 * therefore runs on the bounded elastic scheduler, meant for exactly this kind of work.
 */
@Component
@RequiredArgsConstructor
public class ReactiveMonitorClient {

    private final MonitorClient client;

    public Flux<MonitorTarget> due(int limit) {
        return Mono.fromCallable(() -> client.due(limit))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapIterable(targets -> targets);
    }

    public Mono<Void> report(Long monitorId, ProbeOutcome outcome) {
        return Mono.<Void>fromRunnable(() -> client.report(monitorId, outcome))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Boolean> exists(Long monitorId) {
        return Mono.fromCallable(() -> client.exists(monitorId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
