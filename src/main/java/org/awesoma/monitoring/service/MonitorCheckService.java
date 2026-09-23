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

@Service
@RequiredArgsConstructor
public class MonitorCheckService {

    private final MonitorRepository monitorRepository;
    private final CheckResultRepository checkResultRepository;
    private final IncidentRepository incidentRepository;
    private final ChannelRepository channelRepository;
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<MonitorTarget> findDueTargets(int limit) {
        return monitorRepository.findAllById(monitorRepository.findDueMonitorIds(limit)).stream()
                .map(monitor -> new MonitorTarget(
                        monitor.getId(),
                        monitor.getUrl(),
                        monitor.getHttpMethod(),
                        monitor.getTimeoutMs(),
                        monitor.getExpectedStatus()))
                .toList();
    }

    @Transactional
    public void record(Long monitorId, ProbeOutcome outcome) {
        Monitor monitor = monitorRepository.findByIdForUpdate(monitorId).orElse(null);
        if (monitor == null) {
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
        checkResultRepository.save(result);
    }

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
        incidentRepository.save(incident);

        notifyEnabledChannels(monitor, incident);
    }

    private void resolveOpenIncident(Monitor monitor, OffsetDateTime at) {
        boolean wasDown = monitor.getCurrentState() == MonitorState.DOWN;
        monitor.setCurrentState(MonitorState.UP);
        if (!wasDown) {
            return;
        }
        incidentRepository
                .findByMonitorIdAndStatus(monitor.getId(), IncidentStatus.OPEN)
                .ifPresent(incident -> {
                    incident.resolve(at);
                    notifyEnabledChannels(monitor, incident);
                });
    }

    private void notifyEnabledChannels(Monitor monitor, Incident incident) {
        channelRepository.findByProjectIdAndEnabledTrue(monitor.getProject().getId()).forEach(channel -> {
            Notification notification = new Notification();
            notification.setIncident(incident);
            notification.setChannel(channel);
            notificationRepository.save(notification);
        });
    }
}
