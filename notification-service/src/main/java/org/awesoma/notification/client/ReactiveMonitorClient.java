package org.awesoma.notification.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Feign blocks, so its calls run on the bounded elastic scheduler, off the event loop. */
@Component
@RequiredArgsConstructor
public class ReactiveMonitorClient {

    private final MonitorClient client;

    public Mono<Boolean> projectExists(Long projectId) {
        return Mono.fromCallable(() -> client.projectExists(projectId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Boolean> incidentExists(Long incidentId) {
        return Mono.fromCallable(() -> client.incidentExists(incidentId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
