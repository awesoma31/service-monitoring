package org.awesoma.notification.application.usecase;

import org.awesoma.notification.application.model.IncidentNotice;
import org.awesoma.notification.application.model.ResourceEvent;
import org.awesoma.notification.application.port.ProjectGuard;
import org.awesoma.notification.application.port.UnitOfWork;
import reactor.core.publisher.Mono;

/** Event id and resulting changes are stored in the same unit of work. */
public class NotificationEventHandler {
    private final UnitOfWork work;
    private final ProjectGuard projects;
    private final ChannelService channels;
    private final NotificationService notifications;
    public NotificationEventHandler(UnitOfWork work, ProjectGuard projects, ChannelService channels,
            NotificationService notifications) {
        this.work = work; this.projects = projects; this.channels = channels; this.notifications = notifications;
    }
    public Mono<Void> handle(ResourceEvent event) {
        return work.write(() -> {
            if (!projects.recordEvent(event.id())) return 0;
            return switch (event.type()) {
                case MONITOR_DELETED -> 0;
                case PROJECT_DELETED -> channels.deleteByProjectInTransaction(event.projectId());
                case INCIDENT_CHANGED -> notifications.recordInTransaction(
                        new IncidentNotice(event.incidentId(), event.projectId(), event.kind()));
            };
        }).then();
    }
}
