package org.awesoma.notification.application.usecase;

import org.awesoma.notification.application.exception.NotFoundException;
import org.awesoma.notification.application.model.IncidentNotice;
import org.awesoma.notification.application.model.PageResult;
import org.awesoma.notification.application.port.ChannelStore;
import org.awesoma.notification.application.port.MonitorDirectory;
import org.awesoma.notification.application.port.NotificationStore;
import org.awesoma.notification.application.port.ProjectGuard;
import org.awesoma.notification.application.port.UnitOfWork;
import org.awesoma.notification.domain.IncidentKind;
import org.awesoma.notification.domain.Notification;
import reactor.core.publisher.Mono;

public class NotificationService {
    private final NotificationStore notifications;
    private final ChannelStore channels;
    private final MonitorDirectory monitors;
    private final UnitOfWork work;
    private final ProjectGuard projects;

    public NotificationService(NotificationStore notifications, ChannelStore channels, MonitorDirectory monitors,
            UnitOfWork work, ProjectGuard projects) {
        this.notifications = notifications;
        this.channels = channels;
        this.monitors = monitors;
        this.work = work;
        this.projects = projects;
    }

    public Mono<Integer> record(IncidentNotice event) {
        return work.write(() -> recordInTransaction(event));
    }

    /** The caller's unit of work includes the inbox entry when consuming an event. */
    public int recordInTransaction(IncidentNotice event) {
        projects.lock(event.projectId());
        if (projects.deleted(event.projectId())) {
            return 0;
        }
        String state = event.kind() == IncidentKind.OPENED ? "opened" : "resolved";
        String subject = "Incident #%d %s".formatted(event.incidentId(), state);
        String message = "Incident #%d in project #%d was %s.".formatted(event.incidentId(), event.projectId(), state);
        var created = channels.findByProjectIdAndEnabledTrue(event.projectId()).stream()
                .map(channel -> new Notification(event.incidentId(), channel, event.kind(), subject, message))
                .toList();
        return notifications.saveAll(created).size();
    }

    public Mono<PageResult<Notification>> listByIncident(Long id, int page, int size) {
        return monitors.incidentExists(id).flatMap(exists -> exists
                ? work.read(() -> notifications.pageByIncident(id, page, size))
                : Mono.error(new NotFoundException("Incident %d not found".formatted(id))));
    }
}
