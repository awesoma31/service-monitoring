package org.awesoma.monitoring.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import org.awesoma.integration.IntegrationEvent;

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
            public void integrationEvent(IntegrationEvent event) {
                throw new IllegalStateException("notification-service unavailable", cause);
            }
            @Override
            public HealthStatus health() {
                log.warn("notification-service health unavailable", cause);
                return HealthStatus.unavailable();
            }

            @Override
            public void incidentChanged(IncidentChanged event) {
                log.error("notification-service unavailable, incident not announced: incident_id={} project_id={} kind={}",
                        event.incidentId(), event.projectId(), event.kind(), cause);
            }

            @Override
            public void deleteChannels(Long projectId) {
                log.error("notification-service unavailable, channels left behind: project_id={}", projectId, cause);
            }
        };
    }
}
