package org.awesoma.user.adapters.out.persistence;

import java.util.function.Supplier;
import org.awesoma.user.application.port.Transactions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringTransactions implements Transactions {
    private final TransactionTemplate read;
    private final TransactionTemplate write;

    public SpringTransactions(PlatformTransactionManager manager) {
        read = new TransactionTemplate(manager);
        read.setReadOnly(true);
        write = new TransactionTemplate(manager);
    }

    @Override
    public <T> T read(Supplier<T> work) {
        return read.execute(status -> work.get());
    }

    @Override
    public <T> T write(Supplier<T> work) {
        return write.execute(status -> work.get());
    }

    @Override
    public void afterCommit(Runnable work) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            work.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                work.run();
            }
        });
    }
}
