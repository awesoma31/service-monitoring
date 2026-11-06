package org.awesoma.monitoring.adapters.out.http;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.awesoma.integration.IntegrationEvent;

/** Internal API of check-service, resolved through Eureka by its service name. */
@FeignClient(
        name = "check-service",
        fallbackFactory = CheckHistoryClientFallbackFactory.class)
public interface CheckHistoryClient {

    @PostMapping("/internal/integration/events")
    void integrationEvent(@RequestBody IntegrationEvent event);

    @DeleteMapping("/internal/results")
    void deleteHistory(@RequestParam("monitor_id") Long monitorId);

    @GetMapping("/actuator/health")
    HealthStatus health();
}
