package org.awesoma.check.adapters.out.persistence;

import lombok.RequiredArgsConstructor;
import org.awesoma.check.application.port.ReactiveUnitOfWork;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class R2dbcUnitOfWork implements ReactiveUnitOfWork {
    private final TransactionalOperator transaction;
    public <T> Mono<T> write(Mono<T> operation) { return transaction.transactional(operation); }
}
