package org.awesoma.check.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awesoma.check.application.model.DeletionEvent;
import org.awesoma.check.application.port.DeletionStore;
import org.awesoma.check.application.port.ReactiveUnitOfWork;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ResourceDeletionServiceTest {
    @Test
    void deletesHistoriesOnceAndAcquiresLocksInConsistentOrder() {
        var accepted = new AtomicBoolean();
        List<Long> deleted = new ArrayList<>();
        DeletionStore store = new DeletionStore() {
            public Mono<Boolean> recordEvent(UUID id) {
                return Mono.fromSupplier(() -> accepted.compareAndSet(false, true));
            }
            public Mono<Void> deleteMonitor(Long id) {
                return Mono.fromRunnable(() -> deleted.add(id));
            }
        };
        ReactiveUnitOfWork work = new ReactiveUnitOfWork() {
            public <T> Mono<T> write(Mono<T> operation) { return operation; }
        };
        var service = new ResourceDeletionService(store, work);
        var event = new DeletionEvent(UUID.randomUUID(), List.of(8L, 3L, 8L));

        StepVerifier.create(service.handle(event)).verifyComplete();
        StepVerifier.create(service.handle(event)).verifyComplete();
        assertThat(deleted).containsExactly(3L, 8L);
    }
}
