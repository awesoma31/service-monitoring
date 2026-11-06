package org.awesoma.report.adapters.in.integration;

import lombok.RequiredArgsConstructor;
import org.awesoma.integration.IntegrationEvent;
import org.awesoma.report.application.usecase.ProjectDeletionService;
import org.springframework.stereotype.Component;

/**
 * Turns a resource event into a use case call, whatever transport brought it. A malformed
 * event is an {@link IllegalArgumentException}: HTTP answers 400 and Kafka sends it to the DLT.
 */
@Component
@RequiredArgsConstructor
public class ResourceEventReceiver {
    private final ProjectDeletionService deletions;

    public void receive(IntegrationEvent event) {
        if (event == null) throw new IllegalArgumentException("Missing event");
        event.validate();
        switch (event.type()) {
            case IntegrationEvent.PROJECT_DELETED -> deletions.projectDeleted(event.eventId(), event.projectId());
            case IntegrationEvent.MONITOR_DELETED -> deletions.monitorDeleted(event.eventId());
            default -> throw new IllegalArgumentException("Unexpected event type: " + event.type());
        }
    }
}
