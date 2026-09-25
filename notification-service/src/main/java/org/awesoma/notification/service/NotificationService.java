package org.awesoma.notification.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.awesoma.notification.client.ReactiveMonitorClient;
import org.awesoma.notification.domain.Notification;
import org.awesoma.notification.repository.ChannelRepository;
import org.awesoma.notification.repository.NotificationRepository;
import org.awesoma.notification.support.JpaExecutor;
import org.awesoma.notification.web.dto.IncidentEvent;
import org.awesoma.notification.web.dto.NotificationResponse;
import org.awesoma.notification.web.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final ChannelRepository channels;
    private final ReactiveMonitorClient monitorService;
    private final JpaExecutor jpa;

    /**
     * Queues one alert per enabled channel of the project, in one transaction: either every
     * channel gets its notification or none does. Delivery itself comes with lab 4.
     */
    public Mono<Integer> record(IncidentEvent event) {
        return jpa.write(() -> {
            List<Notification> created = channels.findByProjectIdAndEnabledTrue(event.projectId())
                    .stream()
                    .map(channel -> new Notification(event.incidentId(), channel))
                    .toList();
            return notifications.saveAll(created).size();
        });
    }

    public Mono<Page<NotificationResponse>> listByIncident(Long incidentId, Pageable pageable) {
        return monitorService.incidentExists(incidentId)
                .flatMap(exists -> exists
                        ? jpa.read(() -> notifications.findByIncidentId(incidentId, pageable)
                                .map(NotificationResponse::of))
                        : Mono.error(new NotFoundException("Incident %d not found".formatted(incidentId))));
    }
}
