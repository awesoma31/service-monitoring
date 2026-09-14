package org.awesoma.monitoring.service;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.domain.entity.CheckResult;
import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Notification;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.awesoma.monitoring.domain.model.MonitorTarget;
import org.awesoma.monitoring.domain.model.ProbeOutcome;
import org.awesoma.monitoring.repository.ChannelRepository;
import org.awesoma.monitoring.repository.CheckResultRepository;
import org.awesoma.monitoring.repository.IncidentRepository;
import org.awesoma.monitoring.repository.MonitorRepository;
import org.awesoma.monitoring.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns probe results into state: check history, monitor state, incidents and the
 * notifications they trigger.
 */
@Service
@RequiredArgsConstructor
public class MonitorCheckService {

    private final MonitorRepository monitors;
    private final CheckResultRepository checkResults;
    private final IncidentRepository incidents;
    private final ChannelRepository channels;
    private final NotificationRepository notifications;

    @Transactional(readOnly = true)
    public List<MonitorTarget> findDueTargets(int limit) {
        return monitors.findAllById(monitors.findDueMonitorIds(limit)).stream()
                .map(monitor -> new MonitorTarget(
                        monitor.getId(),
                        monitor.getUrl(),
                        monitor.getHttpMethod(),
                        monitor.getTimeoutMs(),
                        monitor.getExpectedStatus()))
                .toList();
    }

    /**
     * Records one probe and reacts to it, all in a single transaction.
     *
     * <p>The monitor row is locked for the duration. Two workers probing the same monitor
     * concurrently would otherwise both read the same state and both decide to open an
     * incident; the second would then collide with the partial unique index instead of
     * quietly doing nothing. The lock also keeps a failure and a recovery arriving at the
     * same moment from interleaving into a monitor that is UP with an incident still open.
     *
     * <p>Atomicity matters beyond the race: a monitor marked DOWN without an incident, or
     * an incident without its notifications, is a state no later probe would repair.
     */
    @Transactional
    public void record(Long monitorId, ProbeOutcome outcome) {
        Monitor monitor = monitors.findByIdForUpdate(monitorId).orElse(null);
        if (monitor == null) {
            // Deleted between being scheduled and being probed; nothing to record.
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        monitor.setLastCheckedAt(now);
        saveCheckResult(monitor, outcome, now);

        if (outcome.isFailure()) {
            openIncidentIfAbsent(monitor, outcome, now);
        } else {
            resolveOpenIncident(monitor, now);
        }
    }

    private void saveCheckResult(Monitor monitor, ProbeOutcome outcome, OffsetDateTime at) {
        CheckResult result = new CheckResult();
        result.setMonitor(monitor);
        result.setCheckedAt(at);
        result.setResult(outcome.result());
        result.setResponseMs(outcome.responseMs());
        result.setHttpStatus(outcome.httpStatus());
        result.setErrorMessage(outcome.errorMessage());
        checkResults.save(result);
    }

    /** A monitor already DOWN keeps its incident: a failure is one event, not one per probe. */
    private void openIncidentIfAbsent(Monitor monitor, ProbeOutcome outcome, OffsetDateTime at) {
        if (monitor.getCurrentState() == MonitorState.DOWN) {
            return;
        }
        monitor.setCurrentState(MonitorState.DOWN);

        Incident incident = new Incident();
        incident.setMonitor(monitor);
        incident.setStartedAt(at);
        incident.setSeverity(outcome.severity());
        incident.setCause(outcome.errorMessage());
        incidents.save(incident);

        notifyEnabledChannels(monitor, incident);
    }

    private void resolveOpenIncident(Monitor monitor, OffsetDateTime at) {
        boolean wasDown = monitor.getCurrentState() == MonitorState.DOWN;
        monitor.setCurrentState(MonitorState.UP);
        if (!wasDown) {
            return;
        }
        incidents
                .findByMonitorIdAndStatus(monitor.getId(), IncidentStatus.OPEN)
                .ifPresent(incident -> {
                    incident.resolve(at);
                    notifyEnabledChannels(monitor, incident);
                });
    }

    private void notifyEnabledChannels(Monitor monitor, Incident incident) {
        channels.findByProjectIdAndEnabledTrue(monitor.getProject().getId()).forEach(channel -> {
            Notification notification = new Notification();
            notification.setIncident(incident);
            notification.setChannel(channel);
            notifications.save(notification);
        });
    }
}
