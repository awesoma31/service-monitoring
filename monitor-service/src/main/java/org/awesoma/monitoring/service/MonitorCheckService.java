package org.awesoma.monitoring.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.awesoma.monitoring.integration.IncidentChanged;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Turns probe outcomes reported by check-service into monitor state and incidents, and
 * announces opened and closed incidents to notification-service once they are committed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonitorCheckService {

    private final MonitorRepository monitorRepository;
    private final IncidentRepository incidentRepository;
    private final ApplicationEventPublisher events;

    /** Time a worker gets on top of the probe timeout to store and report the outcome. */
    static final Duration REPORT_GRACE = Duration.ofSeconds(5);

    /**
     * Leases a batch of due monitors to a worker that probes up to {@code concurrency} of them
     * at a time. The lease covers every wave of the batch at its longest timeout, so a worker
     * that never reports blocks the monitors for no longer than the batch could really take.
     */
    @Transactional
    public List<MonitorTarget> claimDueTargets(int limit, int concurrency) {
        List<Monitor> due = monitorRepository.findDueForClaim(limit);
        OffsetDateTime claimedUntil = OffsetDateTime.now().plus(leaseDuration(due, concurrency));
        return due.stream()
                .map(monitor -> claim(monitor, claimedUntil))
                .toList();
    }

    static Duration leaseDuration(List<Monitor> batch, int concurrency) {
        int longestTimeoutMs = batch.stream().mapToInt(Monitor::getTimeoutMs).max().orElse(0);
        long waves = (batch.size() + (long) concurrency - 1) / concurrency;
        return Duration.ofMillis(longestTimeoutMs).plus(REPORT_GRACE).multipliedBy(waves);
    }

    private MonitorTarget claim(Monitor monitor, OffsetDateTime claimedUntil) {
        UUID token = UUID.randomUUID();
        monitor.claimForCheck(token, claimedUntil);
        return new MonitorTarget(
                monitor.getId(),
                monitor.getUrl(),
                monitor.getHttpMethod(),
                monitor.getTimeoutMs(),
                monitor.getExpectedStatus(),
                token);
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
        record(monitorId, outcome, OffsetDateTime.now());
    }

    @Transactional
    public void record(Long monitorId, ProbeOutcome outcome, OffsetDateTime checkedAt) {
        Monitor monitor = monitorRepository.findWithLockById(monitorId).orElse(null);
        if (monitor == null) {
            // Deleted between being scheduled and being probed; nothing to record.
            log.debug("Ignoring outcome for deleted monitor: monitor_id={}", monitorId);
            return;
        }
        // A delayed broker message remains valid until a later claim or edit replaces its token.
        if (outcome.claimToken() == null || !outcome.claimToken().equals(monitor.getCheckClaimToken())) {
            log.info("Ignoring stale or unclaimed outcome: monitor_id={}", monitorId);
            return;
        }
        monitor.clearCheckClaim();
        monitor.setLastCheckedAt(checkedAt);

        if (outcome.isFailure()) {
            openIncidentIfAbsent(monitor, outcome, checkedAt);
        } else {
            resolveOpenIncident(monitor, checkedAt);
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

        publishIncidentChanged(monitor, incident, IncidentChanged.Kind.OPENED);
    }

    private void resolveOpenIncident(Monitor monitor, OffsetDateTime at) {
        boolean mayHaveOpenIncident = monitor.getCurrentState() != MonitorState.UP;
        monitor.setCurrentState(MonitorState.UP);
        if (!mayHaveOpenIncident) {
            return;
        }
        findOpenIncident(monitor).ifPresent(incident -> {
            incident.resolve(at);
            publishIncidentChanged(monitor, incident, IncidentChanged.Kind.RESOLVED);
        });
    }

    private void publishIncidentChanged(
            Monitor monitor, Incident incident, IncidentChanged.Kind kind) {
        Long monitorId = monitor.getId();
        Long projectId = monitor.getProject().getId();
        Long incidentId = incident.getId();
        Runnable logChange = () -> log.info(
                "Incident changed: monitor_id={} project_id={} incident_id={} kind={}",
                monitorId, projectId, incidentId, kind);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    logChange.run();
                }
            });
        } else {
            logChange.run();
        }
        if (monitor.getProject().isOwnerNotificationsEnabled()) {
            events.publishEvent(
                    new IncidentChanged(incident.getId(), monitor.getProject().getId(), kind));
        }
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
