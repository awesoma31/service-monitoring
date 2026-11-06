package org.awesoma.report.adapters.out.http;

import org.awesoma.integration.IntegrationEvent;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** No fallback: a failed delivery stays in the outbox and is sent again with the same ID. */
@FeignClient(name = "notification-service", contextId = "reportNotificationClient")
public interface NotificationClient {

    @PostMapping("/internal/integration/events")
    void integrationEvent(@RequestBody IntegrationEvent event);
}
