package org.awesoma.monitoring.adapters.out.http;

import org.awesoma.integration.IntegrationEvent;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Internal API of report-service. No fallback: a failed delivery must reach the outbox,
 * which keeps the event and sends it again with the same ID.
 */
@FeignClient(name = "report-service", contextId = "reportClient")
public interface ReportClient {

    @PostMapping("/internal/integration/events")
    void integrationEvent(@RequestBody IntegrationEvent event);
}
