package org.awesoma.report.adapters.out.messaging;

import org.awesoma.integration.IntegrationEvent;

public interface ReportEventPublisher {
    String transport();

    void publish(IntegrationEvent event);
}
