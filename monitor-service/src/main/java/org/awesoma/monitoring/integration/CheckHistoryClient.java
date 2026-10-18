package org.awesoma.monitoring.integration;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Internal API of check-service, resolved through Eureka by its service name. */
@FeignClient(
        name = "check-service",
        fallbackFactory = CheckHistoryClientFallbackFactory.class)
public interface CheckHistoryClient {

    @DeleteMapping("/internal/results")
    void deleteHistory(@RequestParam("monitor_id") Long monitorId);

    @GetMapping("/actuator/health")
    HealthStatus health();
}
