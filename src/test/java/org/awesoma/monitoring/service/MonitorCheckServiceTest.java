package org.awesoma.monitoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.awesoma.monitoring.domain.entity.Channel;
import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Notification;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.awesoma.monitoring.domain.enums.Severity;
import org.awesoma.monitoring.domain.model.ProbeOutcome;
import org.awesoma.monitoring.repository.ChannelRepository;
import org.awesoma.monitoring.repository.CheckResultRepository;
import org.awesoma.monitoring.repository.IncidentRepository;
import org.awesoma.monitoring.repository.MonitorRepository;
import org.awesoma.monitoring.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonitorCheckServiceTest {

    @Mock private MonitorRepository monitors;
    @Mock private CheckResultRepository checkResults;
    @Mock private IncidentRepository incidents;
    @Mock private ChannelRepository channels;
    @Mock private NotificationRepository notifications;
    @InjectMocks private MonitorCheckService service;

    @Test
    void aFailureOnAHealthyMonitorOpensAnIncidentAndAlertsEveryEnabledChannel() {
        Monitor monitor = monitor(MonitorState.UP);
        when(monitors.findWithLockById(1L)).thenReturn(Optional.of(monitor));
        when(channels.findByProjectIdAndEnabledTrue(7L)).thenReturn(List.of(new Channel(), new Channel()));

        service.record(1L, ProbeOutcome.timeout(5000, "read timed out"));

        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.DOWN);
        assertThat(monitor.getLastCheckedAt()).isNotNull();

        ArgumentCaptor<Incident> opened = ArgumentCaptor.forClass(Incident.class);
        verify(incidents).save(opened.capture());
        assertThat(opened.getValue().getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(opened.getValue().getCause()).isEqualTo("read timed out");
        assertThat(opened.getValue().getStatus()).isEqualTo(IncidentStatus.OPEN);

        verify(notifications, org.mockito.Mockito.times(2)).save(any(Notification.class));
        verify(checkResults).save(any());
    }

    @Test
    void aSecondFailureDoesNotOpenASecondIncident() {
        Monitor monitor = monitor(MonitorState.DOWN);
        when(monitors.findWithLockById(1L)).thenReturn(Optional.of(monitor));

        service.record(1L, ProbeOutcome.connectionError(10, "refused"));

        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.DOWN);
        verify(incidents, never()).save(any());
        verify(notifications, never()).save(any());
        verify(checkResults).save(any());
    }

    @Test
    void aSuccessAfterAFailureResolvesTheIncidentAndAnnouncesRecovery() {
        Monitor monitor = monitor(MonitorState.DOWN);
        Incident open = new Incident();
        open.setStartedAt(OffsetDateTime.now().minusMinutes(5));
        when(monitors.findWithLockById(1L)).thenReturn(Optional.of(monitor));
        when(incidents.findByMonitorIdAndStatus(monitor.getId(), IncidentStatus.OPEN))
                .thenReturn(Optional.of(open));
        when(channels.findByProjectIdAndEnabledTrue(7L)).thenReturn(List.of(new Channel()));

        service.record(1L, ProbeOutcome.success(120, 200));

        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.UP);
        assertThat(open.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(open.getResolvedAt()).isNotNull();
        verify(notifications).save(any());
    }

    @Test
    void aSuccessOnAHealthyMonitorTouchesNoIncident() {
        Monitor monitor = monitor(MonitorState.UP);
        when(monitors.findWithLockById(1L)).thenReturn(Optional.of(monitor));

        service.record(1L, ProbeOutcome.success(80, 200));

        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.UP);
        verify(incidents, never()).findByMonitorIdAndStatus(any(), any());
        verify(notifications, never()).save(any());
    }

    @Test
    void aMonitorDeletedWhileBeingProbedIsIgnored() {
        when(monitors.findWithLockById(1L)).thenReturn(Optional.empty());

        service.record(1L, ProbeOutcome.success(10, 200));

        verify(checkResults, never()).save(any());
        verify(incidents, never()).save(any());
    }

    private Monitor monitor(MonitorState state) {
        Project project = new Project();
        project.setId(7L);
        Monitor monitor = new Monitor();
        monitor.setId(1L);
        monitor.setProject(project);
        monitor.setCurrentState(state);
        return monitor;
    }
}
