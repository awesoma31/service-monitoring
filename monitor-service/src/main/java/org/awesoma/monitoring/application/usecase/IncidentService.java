package org.awesoma.monitoring.application.usecase;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.awesoma.monitoring.application.event.IncidentChanged;
import org.awesoma.monitoring.application.port.IncidentRepositoryPort;
import org.awesoma.monitoring.application.model.view.IncidentResponse;
import org.awesoma.monitoring.application.exception.ConflictStateException;
import org.awesoma.monitoring.application.exception.NotFoundException;
import org.awesoma.monitoring.application.port.IncidentPresenter;
import org.awesoma.monitoring.application.port.DomainEvents;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;

@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepositoryPort incidents;
    private final MonitorService monitors;
    private final IncidentPresenter mapper;
    private final DomainEvents events;

    public Page<IncidentResponse> listByMonitor(
            Long monitorId, IncidentStatus status, PageRequest pageable) {
        monitors.requireExists(monitorId);
        Page<Incident> page = status == null
                ? incidents.findByMonitorId(monitorId, pageable)
                : incidents.findByMonitorIdAndStatus(monitorId, status, pageable);
        return page.map(mapper::toResponse);
    }

    public IncidentResponse get(Long id) {
        return mapper.toResponse(require(id));
    }

    /**
     * Closes an incident by hand, for a failure someone has dealt with outside the system.
     *
     * <p>The monitor goes back to UNKNOWN rather than UP: an operator saying the incident
     * is handled is not evidence that the site answers, and the next probe will establish
     * that. Leaving it DOWN would be worse — the checker treats a DOWN monitor as already
     * having an incident and would never open another one.
     */
    public IncidentResponse resolve(Long id) {
        Incident incident = require(id);
        if (!incident.isOpen()) {
            throw new ConflictStateException("Incident %d is already resolved".formatted(id));
        }
        incident.resolve(OffsetDateTime.now());

        Monitor monitor = incident.getMonitor();
        if (monitor.getCurrentState() == MonitorState.DOWN) {
            monitor.setCurrentState(MonitorState.UNKNOWN);
        }
        incidents.save(incident);
        monitors.save(monitor);
        if (monitor.getProject().isOwnerNotificationsEnabled()) {
            events.publish(new IncidentChanged(
                    incident.getId(), monitor.getProject().getId(), IncidentChanged.Kind.RESOLVED));
        }
        return mapper.toResponse(incident);
    }

    public boolean exists(Long id) {
        return incidents.existsById(id);
    }

    private Incident require(Long id) {
        return incidents.findById(id).orElseThrow(() -> NotFoundException.of("Incident", id));
    }
}
