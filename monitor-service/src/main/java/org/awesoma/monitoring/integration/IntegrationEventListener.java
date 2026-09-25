package org.awesoma.monitoring.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells the other services what changed here, once the change is committed. A rolled-back
 * transaction therefore never alerts anyone about an incident that did not happen.
 *
 * <p>The calls leave the transaction on purpose: a notification-service outage must not undo
 * an incident. A call that fails is logged; the incident, the monitor and its deletion stand.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationEventListener {

    private final NotificationClient notifications;
    private final CheckHistoryClient checkHistory;

    @TransactionalEventListener
    public void on(IncidentChanged event) {
        call("notify about incident " + event.incidentId(), () -> notifications.incidentChanged(event));
    }

    @TransactionalEventListener
    public void on(MonitorDeleted event) {
        call("delete the history of monitor " + event.monitorId(),
                () -> checkHistory.deleteHistory(event.monitorId()));
    }

    @TransactionalEventListener
    public void on(ProjectDeleted event) {
        event.monitorIds().forEach(monitorId -> call(
                "delete the history of monitor " + monitorId, () -> checkHistory.deleteHistory(monitorId)));
        call("delete the channels of project " + event.projectId(),
                () -> notifications.deleteChannels(event.projectId()));
    }

    private void call(String action, Runnable request) {
        try {
            request.run();
        } catch (RuntimeException failure) {
            log.warn("Could not {}", action, failure);
        }
    }
}
