package org.awesoma.notification.integration;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.awesoma.integration.IntegrationEvent;
import org.awesoma.notification.domain.IncidentKind;
import org.awesoma.notification.repository.ChannelRepository;
import org.awesoma.notification.service.NotificationService;
import org.awesoma.notification.web.dto.IncidentEvent;
import org.awesoma.notification.support.JpaExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Inbox deduplication and notification/channel changes share a single JPA transaction. */
@Service
@RequiredArgsConstructor
public class NotificationEventHandler {
    private final JpaExecutor jpa;
    private final JdbcTemplate db;
    private final ChannelRepository channels;
    private final NotificationService notifications;

    public Mono<Void> handle(IntegrationEvent event) {
        return Mono.defer(() -> {
            if (event == null) throw new IllegalArgumentException("Missing event");
            event.validate();
            if (!List.of(IntegrationEvent.INCIDENT_CHANGED, IntegrationEvent.MONITOR_DELETED,
                    IntegrationEvent.PROJECT_DELETED).contains(event.type())) {
                return Mono.error(new IllegalArgumentException("Unexpected event type"));
            }
            return jpa.write(() -> {
                // The inbox insert also serializes concurrent deliveries of the same id.
                int inserted = db.update("INSERT INTO integration_inbox (event_id) VALUES (?) ON CONFLICT DO NOTHING", event.eventId());
                if (inserted == 0) return 0;
                if (IntegrationEvent.MONITOR_DELETED.equals(event.type())) return 0;
                db.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock(?)) AS acquired", Integer.class, event.projectId());
                if (IntegrationEvent.PROJECT_DELETED.equals(event.type())) {
                    db.update("INSERT INTO deleted_projects (project_id) VALUES (?) ON CONFLICT DO NOTHING", event.projectId());
                    return channels.deleteByProjectId(event.projectId());
                }
                return notifications.recordInTransaction(new IncidentEvent(
                        event.incidentId(), event.projectId(), IncidentKind.valueOf(event.kind())));
            }).then();
        });
    }
}
