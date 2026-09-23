package org.awesoma.monitoring.service;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.awesoma.monitoring.repository.CheckResultRepository;
import org.awesoma.monitoring.repository.IncidentRepository;
import org.awesoma.monitoring.repository.NotificationRepository;
import org.awesoma.monitoring.web.dto.incident.CheckResultResponse;
import org.awesoma.monitoring.web.dto.incident.IncidentResponse;
import org.awesoma.monitoring.web.dto.incident.NotificationResponse;
import org.awesoma.monitoring.web.exception.ConflictStateException;
import org.awesoma.monitoring.web.exception.NotFoundException;
import org.awesoma.monitoring.web.mapper.IncidentMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IncidentService {

    private final IncidentRepository incidents;
    private final CheckResultRepository checkResults;
    private final NotificationRepository notifications;
    private final MonitorService monitors;
    private final IncidentMapper mapper;

    public Page<IncidentResponse> listByMonitor(
            Long monitorId, IncidentStatus status, Pageable pageable) {
        monitors.require(monitorId);
        Page<Incident> page = status == null
                ? incidents.findByMonitorId(monitorId, pageable)
                : incidents.findByMonitorIdAndStatus(monitorId, status, pageable);
        return page.map(mapper::toResponse);
    }

    public IncidentResponse get(Long id) {
        return mapper.toResponse(require(id));
    }

    public Slice<CheckResultResponse> listResults(Long monitorId, Pageable pageable) {
        monitors.require(monitorId);
        return checkResults
                .findByMonitorIdOrderByCheckedAtDesc(monitorId, pageable)
                .map(mapper::toResponse);
    }

    public Page<NotificationResponse> listNotifications(Long incidentId, Pageable pageable) {
        require(incidentId);
        return notifications.findByIncidentId(incidentId, pageable).map(mapper::toResponse);
    }

    @Transactional
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
        return mapper.toResponse(incident);
    }

    private Incident require(Long id) {
        return incidents.findById(id).orElseThrow(() -> NotFoundException.of("Incident", id));
    }
}
