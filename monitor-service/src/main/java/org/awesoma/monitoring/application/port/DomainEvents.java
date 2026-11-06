package org.awesoma.monitoring.application.port;
public interface DomainEvents {
    void publish(Object event);
    void afterCommit(Runnable action);
}
