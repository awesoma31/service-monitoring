package org.awesoma.notification.client;

import org.awesoma.notification.web.exception.ServiceUnavailableException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * While monitor-service is failing or its circuit is open, whether a project or incident
 * exists cannot be answered. The request fails with 503 instead of treating the id as unknown,
 * which would wrongly answer 404 or refuse a valid channel.
 */
@Component
public class MonitorClientFallbackFactory implements FallbackFactory<MonitorClient> {

    @Override
    public MonitorClient create(Throwable cause) {
        return new MonitorClient() {
            @Override
            public boolean projectExists(Long projectId) {
                throw new ServiceUnavailableException("monitor-service", cause);
            }

            @Override
            public boolean incidentExists(Long incidentId) {
                throw new ServiceUnavailableException("monitor-service", cause);
            }
        };
    }
}
