package org.awesoma.check.application.port;

import reactor.core.publisher.Mono;

/** Executes a reactive operation atomically against the local store. */
public interface ReactiveUnitOfWork {
    <T> Mono<T> write(Mono<T> operation);
}
