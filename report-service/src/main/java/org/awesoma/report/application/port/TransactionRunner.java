package org.awesoma.report.application.port;

import java.util.function.Supplier;

/**
 * Runs a short piece of work in one database transaction. Network calls and file transfers
 * stay outside it, so a slow storage or client never holds a transaction open.
 */
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);

    default void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
