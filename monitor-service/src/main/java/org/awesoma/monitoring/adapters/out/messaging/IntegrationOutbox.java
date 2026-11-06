package org.awesoma.monitoring.adapters.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.awesoma.integration.IntegrationEvent;
import org.awesoma.monitoring.application.event.IncidentChanged;
import org.awesoma.monitoring.application.event.MonitorDeleted;
import org.awesoma.monitoring.application.event.ProjectDeleted;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Records integration events in the same database transaction as the business change. */
@Component
@RequiredArgsConstructor
public class IntegrationOutbox {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    @Value("${integration.incident-events.transport:http}") private String incidentTransport;
    @Value("${integration.resource-events.transport:http}") private String resourceTransport;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(IncidentChanged incident) {
        append("incident", incidentTransport, new IntegrationEvent(UUID.randomUUID(), 1,
                IntegrationEvent.INCIDENT_CHANGED, OffsetDateTime.now(), null, incident.projectId(),
                incident.incidentId(), incident.kind().name(), null, null));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(MonitorDeleted monitor) {
        append("resource", resourceTransport, new IntegrationEvent(UUID.randomUUID(), 1,
                IntegrationEvent.MONITOR_DELETED, OffsetDateTime.now(), monitor.monitorId(), null,
                null, null, null, null));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(ProjectDeleted project) {
        append("resource", resourceTransport, new IntegrationEvent(UUID.randomUUID(), 1,
                IntegrationEvent.PROJECT_DELETED, OffsetDateTime.now(), null, project.projectId(),
                null, null, project.monitorIds(), null));
    }

    private void append(String flow, String transport, IntegrationEvent event) {
        event.validate();
        try {
            db.update("INSERT INTO integration_outbox (event_id, flow, transport, payload) VALUES (?, ?, ?, ?)",
                    event.eventId(), flow, transport, json.writeValueAsString(event));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot serialize integration event", failure);
        }
    }
}
