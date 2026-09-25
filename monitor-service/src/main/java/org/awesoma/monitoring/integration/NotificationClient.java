package org.awesoma.monitoring.integration;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Internal API of notification-service, resolved through Eureka by its service name. */
@FeignClient(name = "notification-service", path = "/internal")
public interface NotificationClient {

    @PostMapping("/notifications")
    void incidentChanged(@RequestBody IncidentChanged event);

    @DeleteMapping("/projects/{id}/channels")
    void deleteChannels(@PathVariable("id") Long projectId);
}
