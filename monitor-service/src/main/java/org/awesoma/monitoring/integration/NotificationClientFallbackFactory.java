package org.awesoma.monitoring.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * While notification-service is failing or its circuit is open, the announcement is logged
 * and dropped: the incident itself is committed and stays correct. Delivery that survives
 * an outage comes with the message broker in lab 4.
 */
@Slf4j
@Component
public class NotificationClientFallbackFactory implements FallbackFactory<NotificationClient> {

    @Override
    public NotificationClient create(Throwable cause) {
        return new NotificationClient() {
            @Override
            public void incidentChanged(IncidentChanged event) {
                log.warn("notification-service unavailable, incident {} {} not announced: {}",
                        event.incidentId(), event.kind(), cause.toString());
            }

            @Override
            public void deleteChannels(Long projectId) {
                log.warn("notification-service unavailable, channels of project {} left behind: {}",
                        projectId, cause.toString());
            }
        };
    }
}
