package org.awesoma.notification.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Internal API of monitor-service, resolved through Eureka by its service name. */
@FeignClient(name = "monitor-service", path = "/internal")
public interface MonitorClient {

    @GetMapping("/projects/{id}/exists")
    boolean projectExists(@PathVariable("id") Long projectId);

    @GetMapping("/incidents/{id}/exists")
    boolean incidentExists(@PathVariable("id") Long incidentId);
}
