package org.awesoma.integration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Versioned transport-neutral contract. A stable event id is reused for every retry. */
public record IntegrationEvent(
        UUID eventId,
        int schemaVersion,
        String type,
        OffsetDateTime occurredAt,
        Long monitorId,
        Long projectId,
        Long incidentId,
        String kind,
        List<Long> monitorIds,
        CheckOutcome outcome) {

    public static final String CHECK_COMPLETED = "CheckCompleted";
    public static final String INCIDENT_CHANGED = "IncidentChanged";
    public static final String MONITOR_DELETED = "MonitorDeleted";
    public static final String PROJECT_DELETED = "ProjectDeleted";

    public record CheckOutcome(String result, Integer responseMs, Integer httpStatus,
                               String errorMessage, UUID claimToken) {
        public void validate() {
            if (result == null || !List.of("SUCCESS", "BAD_STATUS", "TIMEOUT", "CONNECTION_ERROR").contains(result)
                    || claimToken == null || (responseMs != null && responseMs < 0)
                    || (httpStatus != null && (httpStatus < 100 || httpStatus > 599))
                    || (errorMessage != null && errorMessage.length() > 500)) {
                throw new IllegalArgumentException("Invalid check outcome");
            }
        }
    }

    public void validate() {
        if (eventId == null || schemaVersion != 1 || type == null || occurredAt == null) {
            throw new IllegalArgumentException("Invalid event envelope");
        }
        switch (type) {
            case CHECK_COMPLETED -> {
                positive(monitorId);
                if (outcome == null) throw new IllegalArgumentException("Missing check outcome");
                outcome.validate();
            }
            case INCIDENT_CHANGED -> {
                positive(projectId);
                positive(incidentId);
                if (!"OPENED".equals(kind) && !"RESOLVED".equals(kind)) {
                    throw new IllegalArgumentException("Invalid incident kind");
                }
            }
            case MONITOR_DELETED -> positive(monitorId);
            case PROJECT_DELETED -> {
                positive(projectId);
                if (monitorIds == null) throw new IllegalArgumentException("Missing monitor ids");
                monitorIds.forEach(IntegrationEvent::positive);
            }
            default -> throw new IllegalArgumentException("Unknown event type: " + type);
        }
    }

    private static void positive(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Invalid resource id");
    }
}
