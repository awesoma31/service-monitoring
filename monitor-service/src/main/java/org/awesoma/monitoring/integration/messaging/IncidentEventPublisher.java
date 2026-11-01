package org.awesoma.monitoring.integration.messaging;

import org.awesoma.integration.IntegrationEvent;

public interface IncidentEventPublisher {
    String transport();
    void publish(IntegrationEvent event);
}
