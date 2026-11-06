package org.awesoma.monitoring.adapters.out.messaging;

import org.awesoma.integration.IntegrationEvent;

public interface ResourceEventPublisher {
    String transport();
    void publish(IntegrationEvent event);
}
