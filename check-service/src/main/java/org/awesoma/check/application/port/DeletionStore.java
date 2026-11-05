package org.awesoma.check.application.port;

import java.util.UUID;
import reactor.core.publisher.Mono;

public interface DeletionStore {
    Mono<Boolean> recordEvent(UUID id);
    Mono<Void> deleteMonitor(Long id);
}
