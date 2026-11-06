package org.awesoma.notification.adapters.in.messaging;

import org.awesoma.integration.IntegrationEvent;
import org.awesoma.notification.application.model.ReportUploadNotice;
import org.awesoma.notification.application.model.ResourceEvent;
import org.awesoma.notification.domain.IncidentKind;

/** Maps and validates the external event envelope at the delivery boundary. */
public final class ResourceEventMapper {
    private ResourceEventMapper() { }
    public static ResourceEvent from(IntegrationEvent event) {
        if (event == null) throw new IllegalArgumentException("Missing event");
        event.validate();
        return switch (event.type()) {
            case IntegrationEvent.INCIDENT_CHANGED -> new ResourceEvent(event.eventId(), ResourceEvent.Type.INCIDENT_CHANGED,
                    event.projectId(), event.incidentId(), IncidentKind.valueOf(event.kind()));
            case IntegrationEvent.MONITOR_DELETED -> new ResourceEvent(event.eventId(), ResourceEvent.Type.MONITOR_DELETED, null, null, null);
            case IntegrationEvent.PROJECT_DELETED -> new ResourceEvent(event.eventId(), ResourceEvent.Type.PROJECT_DELETED, event.projectId(), null, null);
            case IntegrationEvent.REPORT_UPLOADED -> new ResourceEvent(event.eventId(), ResourceEvent.Type.REPORT_UPLOADED,
                    event.projectId(), null, null, new ReportUploadNotice(event.projectId(), event.reportId(),
                            event.fileName(), event.uploadedBy()));
            default -> throw new IllegalArgumentException("Unexpected event type");
        };
    }
}
