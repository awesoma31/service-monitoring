package org.awesoma.notification.application.model;

import java.util.UUID;
import org.awesoma.notification.domain.IncidentKind;

/** Internal application representation independent of the integration envelope. */
public record ResourceEvent(UUID id, Type type, Long projectId, Long incidentId, IncidentKind kind,
        ReportUploadNotice report) {
    public enum Type { INCIDENT_CHANGED, MONITOR_DELETED, PROJECT_DELETED, REPORT_UPLOADED }

    public ResourceEvent(UUID id, Type type, Long projectId, Long incidentId, IncidentKind kind) {
        this(id, type, projectId, incidentId, kind, null);
    }
}
