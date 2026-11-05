package org.awesoma.notification.application.port;

import java.util.function.Supplier;
import reactor.core.publisher.Mono;
public interface UnitOfWork {
    <T> Mono<T> read(Supplier<T> work);
    <T> Mono<T> write(Supplier<T> work);
}
