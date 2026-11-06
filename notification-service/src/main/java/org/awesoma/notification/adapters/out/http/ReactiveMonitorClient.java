package org.awesoma.notification.adapters.out.http;

import lombok.RequiredArgsConstructor;
import org.awesoma.notification.application.port.MonitorDirectory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Feign blocks, so its calls run on the bounded elastic scheduler, off the event loop. */
@Component
@RequiredArgsConstructor
public class ReactiveMonitorClient implements MonitorDirectory {

    private final MonitorClient client;

    @Override
    public Mono<Boolean> projectExists(Long projectId) {
        return Mono.fromCallable(() -> client.projectExists(projectId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> incidentExists(Long incidentId) {
        return Mono.fromCallable(() -> client.incidentExists(incidentId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
