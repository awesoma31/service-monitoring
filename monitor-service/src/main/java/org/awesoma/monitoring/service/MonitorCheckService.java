package org.awesoma.monitoring.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.awesoma.monitoring.domain.model.MonitorTarget;
import org.awesoma.monitoring.domain.model.ProbeOutcome;
import org.awesoma.monitoring.repository.IncidentRepository;
import org.awesoma.monitoring.repository.MonitorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.awesoma.monitoring.integration.IncidentChanged;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Turns probe outcomes reported by check-service into monitor state and incidents, and
 * announces opened and closed incidents to notification-service once they are committed.
 */
@Service
@RequiredArgsConstructor
public class MonitorCheckService {

    private final MonitorRepository monitorRepository;
    private final IncidentRepository incidentRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<MonitorTarget> findDueTargets(int limit) {
        return monitorRepository.findDue(limit).stream()
                .map(monitor -> new MonitorTarget(
                        monitor.getId(),
                        monitor.getUrl(),
                        monitor.getHttpMethod(),
                        monitor.getTimeoutMs(),
                        monitor.getExpectedStatus()))
                .toList();
    }

    /**
     * Applies the outcome of one probe, reported by the check service, in a single
     * transaction. The probe itself and its check history live in that service.
     *
     * <p>The monitor row is locked for the duration. Two workers probing the same monitor
     * concurrently would otherwise both read the same state and both decide to open an
     * incident; the second would then collide with the partial unique index instead of
     * quietly doing nothing. The lock also keeps a failure and a recovery arriving at the
     * same moment from interleaving into a monitor that is UP with an incident still open.
     *
     * <p>Atomicity matters beyond the race: a monitor marked DOWN without an incident is a
     * state no later probe would repair. Notifications are announced only after commit, so a
     * rolled-back outcome never alerts anyone.
     */
    @Transactional
    public void record(Long monitorId, ProbeOutcome outcome) {
        Monitor monitor = monitorRepository.findWithLockById(monitorId).orElse(null);
        if (monitor == null) {
            // Deleted between being scheduled and being probed; nothing to record.
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        monitor.setLastCheckedAt(now);

        if (outcome.isFailure()) {
            openIncidentIfAbsent(monitor, outcome, now);
        } else {
            resolveOpenIncident(monitor, now);
        }
    }

    /** A monitor already DOWN keeps its incident: a failure is one event, not one per probe. */
    private void openIncidentIfAbsent(Monitor monitor, ProbeOutcome outcome, OffsetDateTime at) {
        MonitorState state = monitor.getCurrentState();
        boolean incidentAlreadyOpen = state == MonitorState.DOWN
                || (state != MonitorState.UP && findOpenIncident(monitor).isPresent());
        monitor.setCurrentState(MonitorState.DOWN);
        if (incidentAlreadyOpen) {
            return;
        }

        Incident incident = new Incident();
        incident.setMonitor(monitor);
        incident.setStartedAt(at);
        incident.setSeverity(outcome.severity());
        incident.setCause(outcome.errorMessage());
        incidentRepository.save(incident);

        events.publishEvent(new IncidentChanged(
                incident.getId(), monitor.getProject().getId(), IncidentChanged.Kind.OPENED));
    }

    private void resolveOpenIncident(Monitor monitor, OffsetDateTime at) {
        boolean mayHaveOpenIncident = monitor.getCurrentState() != MonitorState.UP;
        monitor.setCurrentState(MonitorState.UP);
        if (!mayHaveOpenIncident) {
            return;
        }
        findOpenIncident(monitor).ifPresent(incident -> {
            incident.resolve(at);
            events.publishEvent(new IncidentChanged(
                    incident.getId(), monitor.getProject().getId(), IncidentChanged.Kind.RESOLVED));
        });
    }

    /**
     * The monitor's state alone does not tell whether an incident is open: pausing a monitor
     * that is down, or resuming it afterwards, changes the state but leaves the incident open.
     * Only UP and DOWN are conclusive, so any other state asks the database.
     */
    private Optional<Incident> findOpenIncident(Monitor monitor) {
        return incidentRepository.findByMonitorIdAndStatus(monitor.getId(), IncidentStatus.OPEN);
    }
}
