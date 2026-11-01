package org.awesoma.monitoring.integration.messaging;

import org.awesoma.integration.IntegrationEvent;

public interface ResourceEventPublisher {
    String transport();
    void publish(IntegrationEvent event);
}
