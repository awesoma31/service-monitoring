package org.awesoma.monitoring.integration;

/** Published inside the incident transaction; delivered to notification-service after commit. */
public record IncidentChanged(Long incidentId, Long projectId, Kind kind) {

    public enum Kind {
        OPENED,
        RESOLVED
    }
}
