package org.awesoma.monitoring.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * While check-service is failing or its circuit is open, the history of a deleted monitor is
 * left in place. Nothing reads it once the monitor is gone, so the leftover rows are harmless.
 */
@Slf4j
@Component
public class CheckHistoryClientFallbackFactory implements FallbackFactory<CheckHistoryClient> {

    @Override
    public CheckHistoryClient create(Throwable cause) {
        return new CheckHistoryClient() {
            @Override
            public void deleteHistory(Long monitorId) {
                log.error("check-service unavailable, history left behind: monitor_id={}", monitorId, cause);
            }

            @Override
            public HealthStatus health() {
                log.warn("check-service health unavailable", cause);
                return HealthStatus.unavailable();
            }
        };
    }
}
