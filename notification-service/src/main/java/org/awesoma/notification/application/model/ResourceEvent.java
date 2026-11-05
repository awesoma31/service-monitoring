package org.awesoma.notification.application.model;

import java.util.UUID;
import org.awesoma.notification.domain.IncidentKind;

/** Internal application representation independent of the integration envelope. */
public record ResourceEvent(UUID id, Type type, Long projectId, Long incidentId, IncidentKind kind) {
    public enum Type { INCIDENT_CHANGED, MONITOR_DELETED, PROJECT_DELETED }
}
