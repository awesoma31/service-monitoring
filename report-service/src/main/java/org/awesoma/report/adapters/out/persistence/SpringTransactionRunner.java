package org.awesoma.report.adapters.out.persistence;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.awesoma.report.application.port.TransactionRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
class SpringTransactionRunner implements TransactionRunner {
    private final TransactionTemplate transactions;

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
