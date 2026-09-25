package org.awesoma.notification.web;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.awesoma.notification.service.ChannelService;
import org.awesoma.notification.service.NotificationService;
import org.awesoma.notification.web.dto.IncidentEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Called by monitor-service only: the gateway routes /api/v1/** alone, so this is reachable
 * inside the service network, and it stays out of the API docs.
 */
@Hidden
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final NotificationService notificationService;
    private final ChannelService channelService;

    @PostMapping("/notifications")
    public Mono<ResponseEntity<Void>> incidentChanged(@Valid @RequestBody IncidentEvent event) {
        return notificationService.record(event).thenReturn(ResponseEntity.noContent().<Void>build());
    }

    @DeleteMapping("/projects/{projectId}/channels")
    public Mono<ResponseEntity<Void>> projectDeleted(@PathVariable @Positive Long projectId) {
        return channelService.deleteByProject(projectId).thenReturn(ResponseEntity.noContent().<Void>build());
    }
}
