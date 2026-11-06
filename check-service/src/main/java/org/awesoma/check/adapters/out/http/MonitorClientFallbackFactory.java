package org.awesoma.check.adapters.out.http;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.check.domain.MonitorTarget;
import org.awesoma.check.domain.ProbeOutcome;
import org.awesoma.check.application.exception.ServiceUnavailableException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import org.awesoma.integration.IntegrationEvent;

/**
 * What check-service does while monitor-service is failing or its circuit is open:
 * <ul>
 *   <li>no monitors are due, so the pass is skipped and retried on the next tick;
 *   <li>an outcome that cannot be reported is dropped: the result is already in the history,
 *       and the monitor's state catches up with the next probe;
 *   <li>whether a monitor exists cannot be answered, so the history request fails with 503
 *       rather than claiming the monitor is missing.
 * </ul>
 */
@Slf4j
@Component
public class MonitorClientFallbackFactory implements FallbackFactory<MonitorClient> {

    @Override
    public MonitorClient create(Throwable cause) {
        return new MonitorClient() {
            @Override
            public void reportEvent(IntegrationEvent event) {
                throw new ServiceUnavailableException("monitor-service", cause);
            }
            @Override
            public List<MonitorTarget> due(int limit, int concurrency) {
                log.warn("monitor-service unavailable, skipping check pass: batch_size={} concurrency={}",
                        limit, concurrency, cause);
                return List.of();
            }

            @Override
            public void report(Long monitorId, ProbeOutcome outcome) {
                log.error("monitor-service unavailable, outcome not applied: monitor_id={}",
                        monitorId, cause);
            }

            @Override
            public boolean exists(Long monitorId) {
                log.warn("monitor-service unavailable, monitor lookup failed: monitor_id={}", monitorId, cause);
                throw new ServiceUnavailableException("monitor-service", cause);
            }
        };
    }
}
