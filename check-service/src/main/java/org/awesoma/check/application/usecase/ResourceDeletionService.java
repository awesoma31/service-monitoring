package org.awesoma.check.application.usecase;

import org.awesoma.check.application.model.DeletionEvent;
import org.awesoma.check.application.port.DeletionStore;
import org.awesoma.check.application.port.ReactiveUnitOfWork;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Deletes histories once per event; sorted locks avoid deadlocks for overlapping projects. */
public class ResourceDeletionService {
    private final DeletionStore store;
    private final ReactiveUnitOfWork work;

    public ResourceDeletionService(DeletionStore store, ReactiveUnitOfWork work) {
        this.store = store;
        this.work = work;
    }

    public Mono<Void> handle(DeletionEvent event) {
        return work.write(store.recordEvent(event.id()).flatMap(inserted -> inserted
                ? Flux.fromIterable(event.monitorIds().stream().distinct().sorted().toList())
                    .concatMap(store::deleteMonitor).then()
                : Mono.empty()));
    }

    public Mono<Void> deleteMonitor(Long monitorId) {
        return work.write(store.deleteMonitor(monitorId));
    }
}
