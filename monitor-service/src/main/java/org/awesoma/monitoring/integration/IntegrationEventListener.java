package org.awesoma.monitoring.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells the other services what changed here, once the change is committed. A rolled-back
 * transaction therefore never alerts anyone about an incident that did not happen.
 *
 * <p>The calls leave the transaction on purpose: a notification-service outage must not undo
 * an incident. Each client has a circuit breaker whose fallback logs the call that could not
 * be made, so a failure never reaches the request that committed the change.
 */
@Component
@RequiredArgsConstructor
public class IntegrationEventListener {

    private final NotificationClient notifications;
    private final CheckHistoryClient checkHistory;

    @TransactionalEventListener
    public void on(IncidentChanged event) {
        notifications.incidentChanged(event);
    }

    @TransactionalEventListener
    public void on(MonitorDeleted event) {
        checkHistory.deleteHistory(event.monitorId());
    }

    @TransactionalEventListener
    public void on(ProjectDeleted event) {
        event.monitorIds().forEach(checkHistory::deleteHistory);
        notifications.deleteChannels(event.projectId());
    }
}
