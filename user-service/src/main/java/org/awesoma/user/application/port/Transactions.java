package org.awesoma.user.application.port;
import java.util.function.Supplier;
public interface Transactions {
    <T> T write(Supplier<T> work);
    <T> T read(Supplier<T> work);
    void afterCommit(Runnable work);
}
