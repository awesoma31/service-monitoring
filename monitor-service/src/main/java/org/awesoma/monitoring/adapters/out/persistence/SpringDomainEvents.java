package org.awesoma.monitoring.adapters.out.persistence;

import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.application.port.DomainEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class SpringDomainEvents implements DomainEvents {
    private final ApplicationEventPublisher publisher;

    public void publish(Object event) {
        publisher.publishEvent(event);
    }

    public void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
