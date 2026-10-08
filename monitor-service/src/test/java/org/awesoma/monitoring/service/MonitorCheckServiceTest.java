package org.awesoma.monitoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.awesoma.monitoring.domain.enums.Severity;
import org.awesoma.monitoring.domain.model.ProbeOutcome;
import org.awesoma.monitoring.repository.IncidentRepository;
import org.awesoma.monitoring.repository.MonitorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.awesoma.monitoring.integration.IncidentChanged;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MonitorCheckServiceTest {

    @Mock private MonitorRepository monitors;
    @Mock private IncidentRepository incidents;
    @Mock private ApplicationEventPublisher events;
    @InjectMocks private MonitorCheckService service;

    @Test
    void aFailureOnAHealthyMonitorOpensAnIncidentAndAnnouncesIt() {
        Monitor monitor = monitor(MonitorState.UP);
        when(monitors.findWithLockById(1L)).thenReturn(Optional.of(monitor));

        service.record(1L, claimed(monitor, ProbeOutcome.timeout(5000, "read timed out")));

        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.DOWN);
        assertThat(monitor.getLastCheckedAt()).isNotNull();

        ArgumentCaptor<Incident> opened = ArgumentCaptor.forClass(Incident.class);
        verify(incidents).save(opened.capture());
        assertThat(opened.getValue().getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(opened.getValue().getCause()).isEqualTo("read timed out");
        assertThat(opened.getValue().getStatus()).isEqualTo(IncidentStatus.OPEN);

        verify(events).publishEvent(new IncidentChanged(null, 7L, IncidentChanged.Kind.OPENED));
    }

    @Test
    void aSecondFailureDoesNotOpenASecondIncident() {
        Monitor monitor = monitor(MonitorState.DOWN);
        when(monitors.findWithLockById(1L)).thenReturn(Optional.of(monitor));

        service.record(1L, claimed(monitor, ProbeOutcome.connectionError(10, "refused")));

        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.DOWN);
        verify(incidents, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void aSuccessAfterAFailureResolvesTheIncidentAndAnnouncesRecovery() {
        Monitor monitor = monitor(MonitorState.DOWN);
        Incident open = new Incident();
        open.setStartedAt(OffsetDateTime.now().minusMinutes(5));
        when(monitors.findWithLockById(1L)).thenReturn(Optional.of(monitor));
        when(incidents.findByMonitorIdAndStatus(monitor.getId(), IncidentStatus.OPEN))
                .thenReturn(Optional.of(open));

        service.record(1L, claimed(monitor, ProbeOutcome.success(120, 200)));

        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.UP);
        assertThat(open.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(open.getResolvedAt()).isNotNull();
        verify(events).publishEvent(new IncidentChanged(null, 7L, IncidentChanged.Kind.RESOLVED));
    }

    @Test
    void aSuccessOnAHealthyMonitorTouchesNoIncident() {
        Monitor monitor = monitor(MonitorState.UP);
        when(monitors.findWithLockById(1L)).thenReturn(Optional.of(monitor));

        service.record(1L, claimed(monitor, ProbeOutcome.success(80, 200)));

        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.UP);
        verify(incidents, never()).findByMonitorIdAndStatus(any(), any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void disabledOwnerNotificationsDoNotAnnounceAnOpenedIncident() {
        Monitor monitor = monitor(MonitorState.UP);
        monitor.getProject().setOwnerNotificationsEnabled(false);
        when(monitors.findWithLockById(1L)).thenReturn(Optional.of(monitor));

        service.record(1L, claimed(monitor, ProbeOutcome.connectionError(10, "refused")));

        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.DOWN);
        verify(incidents).save(any(Incident.class));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void disabledOwnerNotificationsDoNotAnnounceARecovery() {
        Monitor monitor = monitor(MonitorState.DOWN);
        monitor.getProject().setOwnerNotificationsEnabled(false);
        Incident open = new Incident();
        open.setStartedAt(OffsetDateTime.now().minusMinutes(5));
        when(monitors.findWithLockById(1L)).thenReturn(Optional.of(monitor));
        when(incidents.findByMonitorIdAndStatus(monitor.getId(), IncidentStatus.OPEN))
                .thenReturn(Optional.of(open));

        service.record(1L, claimed(monitor, ProbeOutcome.success(120, 200)));

        assertThat(open.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void aMonitorDeletedWhileBeingProbedIsIgnored() {
        when(monitors.findWithLockById(1L)).thenReturn(Optional.empty());

        service.record(1L, ProbeOutcome.success(10, 200).forClaim(UUID.randomUUID()));
        verify(incidents, never()).save(any());
    }

    @Test
    void claimingDueMonitorsAssignsUniqueTokensAndADeadline() {
        Monitor first = monitor(MonitorState.UP);
        first.setId(1L);
        Monitor second = monitor(MonitorState.UP);
        second.setId(2L);
        when(monitors.findDueForClaim(2)).thenReturn(List.of(first, second));

        var targets = service.claimDueTargets(2, Duration.ofMinutes(2));

        assertThat(targets).hasSize(2);
        assertThat(targets).extracting(target -> target.claimToken()).doesNotHaveDuplicates();
        assertThat(first.getCheckClaimToken()).isEqualTo(targets.get(0).claimToken());
        assertThat(first.getCheckClaimedUntil()).isAfter(OffsetDateTime.now().plusMinutes(1));
    }

    @Test
    void anOutcomeWithAStaleClaimTokenIsIgnored() {
        Monitor monitor = monitor(MonitorState.UP);
        UUID activeToken = UUID.randomUUID();
        monitor.claimForCheck(activeToken, OffsetDateTime.now().plusMinutes(1));
        when(monitors.findWithLockById(1L)).thenReturn(Optional.of(monitor));

        service.record(
                1L, ProbeOutcome.connectionError(10, "stale").forClaim(UUID.randomUUID()));

        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.UP);
        assertThat(monitor.getCheckClaimToken()).isEqualTo(activeToken);
        assertThat(monitor.getLastCheckedAt()).isNull();
        verify(incidents, never()).save(any());
    }

    @Test
    void anOutcomeWhoseLeaseHasExpiredIsIgnored() {
        Monitor monitor = monitor(MonitorState.UP);
        UUID expiredToken = UUID.randomUUID();
        monitor.claimForCheck(expiredToken, OffsetDateTime.now().minusSeconds(1));
        when(monitors.findWithLockById(1L)).thenReturn(Optional.of(monitor));

        service.record(
                1L, ProbeOutcome.connectionError(10, "late").forClaim(expiredToken));

        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.UP);
        assertThat(monitor.getLastCheckedAt()).isNull();
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

    private ProbeOutcome claimed(Monitor monitor, ProbeOutcome outcome) {
        UUID token = UUID.randomUUID();
        monitor.claimForCheck(token, OffsetDateTime.now().plusMinutes(1));
        return outcome.forClaim(token);
    }
}
