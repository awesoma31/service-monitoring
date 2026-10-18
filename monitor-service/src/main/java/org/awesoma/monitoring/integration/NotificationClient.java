package org.awesoma.monitoring.integration;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Internal API of notification-service, resolved through Eureka by its service name. */
@FeignClient(
        name = "notification-service",
        fallbackFactory = NotificationClientFallbackFactory.class)
public interface NotificationClient {

    @PostMapping("/internal/notifications")
    void incidentChanged(@RequestBody IncidentChanged event);

    @DeleteMapping("/internal/projects/{id}/channels")
    void deleteChannels(@PathVariable("id") Long projectId);

    @GetMapping("/actuator/health")
    HealthStatus health();
}
