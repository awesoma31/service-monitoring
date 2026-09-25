package org.awesoma.monitoring.integration;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Internal API of check-service, resolved through Eureka by its service name. */
@FeignClient(name = "check-service", path = "/internal/results")
public interface CheckHistoryClient {

    @DeleteMapping
    void deleteHistory(@RequestParam("monitor_id") Long monitorId);
}
