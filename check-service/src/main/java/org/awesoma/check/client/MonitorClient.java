package org.awesoma.check.client;

import java.util.List;
import org.awesoma.check.domain.MonitorTarget;
import org.awesoma.check.domain.ProbeOutcome;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** Internal API of monitor-service, resolved through Eureka by its service name. */
@FeignClient(
        name = "monitor-service",
        path = "/internal/monitors",
        fallbackFactory = MonitorClientFallbackFactory.class)
public interface MonitorClient {

    @GetMapping("/due")
    List<MonitorTarget> due(@RequestParam("limit") int limit);

    @PostMapping("/{id}/outcomes")
    void report(@PathVariable("id") Long monitorId, @RequestBody ProbeOutcome outcome);

    @GetMapping("/{id}/exists")
    boolean exists(@PathVariable("id") Long monitorId);
}
