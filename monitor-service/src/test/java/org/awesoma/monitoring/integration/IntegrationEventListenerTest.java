package org.awesoma.monitoring.integration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;

class IntegrationEventListenerTest {

    private final NotificationClient notifications = mock(NotificationClient.class);
    private final CheckHistoryClient checkHistory = mock(CheckHistoryClient.class);
    private final IntegrationEventListener listener =
            new IntegrationEventListener(notifications, checkHistory);

    @Test
    void anIncidentChangeIsAnnouncedToNotificationService() {
        IncidentChanged event = new IncidentChanged(1L, 2L, IncidentChanged.Kind.OPENED);

        listener.on(event);

        verify(notifications).incidentChanged(event);
    }

    @Test
    void aDeletedMonitorLosesItsHistory() {
        listener.on(new MonitorDeleted(5L));

        verify(checkHistory).deleteHistory(5L);
    }

    @Test
    void aDeletedProjectLosesTheHistoryOfEveryMonitorAndItsChannels() {
        listener.on(new ProjectDeleted(9L, List.of(3L, 4L)));

        verify(checkHistory).deleteHistory(3L);
        verify(checkHistory).deleteHistory(4L);
        verify(notifications).deleteChannels(9L);
    }
}
