package org.awesoma.monitoring.integration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
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
    void anUnreachableServiceDoesNotFailTheCommittedChange() {
        IncidentChanged event = new IncidentChanged(1L, 2L, IncidentChanged.Kind.RESOLVED);
        doThrow(new IllegalStateException("connection refused")).when(notifications).incidentChanged(event);

        assertThatCode(() -> listener.on(event)).doesNotThrowAnyException();
    }

    @Test
    void aDeletedMonitorLosesItsHistory() {
        listener.on(new MonitorDeleted(5L));

        verify(checkHistory).deleteHistory(5L);
    }

    @Test
    void aDeletedProjectLosesTheHistoryOfEveryMonitorAndItsChannels() {
        doThrow(new IllegalStateException("down")).when(checkHistory).deleteHistory(3L);

        listener.on(new ProjectDeleted(9L, List.of(3L, 4L)));

        verify(checkHistory).deleteHistory(3L);
        verify(checkHistory).deleteHistory(4L);
        verify(notifications).deleteChannels(9L);
    }
}
