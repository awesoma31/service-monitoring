package org.awesoma.check.client;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.check.domain.MonitorTarget;
import org.awesoma.check.domain.ProbeOutcome;
import org.awesoma.check.web.exception.ServiceUnavailableException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

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
            public List<MonitorTarget> due(int limit) {
                log.warn("monitor-service unavailable, skipping this check pass: {}", cause.toString());
                return List.of();
            }

            @Override
            public void report(Long monitorId, ProbeOutcome outcome) {
                log.warn("monitor-service unavailable, outcome of monitor {} not applied: {}",
                        monitorId, cause.toString());
            }

            @Override
            public boolean exists(Long monitorId) {
                throw new ServiceUnavailableException("monitor-service", cause);
            }
        };
    }
}
