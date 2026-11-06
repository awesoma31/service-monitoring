package org.awesoma.notification.adapters.out.persistence;

import java.util.function.Supplier;
import org.awesoma.notification.application.port.UnitOfWork;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Bridges blocking JPA into the reactive web layer. The work runs in a transaction on the
 * bounded elastic scheduler, so an event-loop thread never waits on the database; the
 * transaction is programmatic because @Transactional does not reach across that hop.
 */
@Component
public class JpaExecutor implements UnitOfWork {

    private final TransactionTemplate readWrite;
    private final TransactionTemplate readOnly;

    public JpaExecutor(PlatformTransactionManager transactionManager) {
        this.readWrite = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public <T> Mono<T> write(Supplier<T> work) {
        return run(readWrite, work);
    }

    @Override
    public <T> Mono<T> read(Supplier<T> work) {
        return run(readOnly, work);
    }

    private static <T> Mono<T> run(TransactionTemplate template, Supplier<T> work) {
        return Mono.fromCallable(() -> template.execute(status -> work.get()))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
