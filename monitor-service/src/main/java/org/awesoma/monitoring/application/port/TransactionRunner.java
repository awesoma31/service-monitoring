package org.awesoma.monitoring.application.port;

import java.util.function.Supplier;
public interface TransactionRunner {
    <T> T read(Supplier<T> action);
    <T> T write(Supplier<T> action);
    default void write(Runnable action) { write(() -> { action.run(); return null; }); }
    void afterCommit(Runnable action);
}
