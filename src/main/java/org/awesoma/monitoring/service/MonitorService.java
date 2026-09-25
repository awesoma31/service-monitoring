package org.awesoma.monitoring.service;

import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.awesoma.monitoring.repository.MonitorRepository;
import org.awesoma.monitoring.web.dto.monitor.MonitorCreateRequest;
import org.awesoma.monitoring.web.dto.monitor.MonitorResponse;
import org.awesoma.monitoring.web.dto.monitor.MonitorUpdateRequest;
import org.awesoma.monitoring.web.exception.ConflictStateException;
import org.awesoma.monitoring.web.exception.NotFoundException;
import org.awesoma.monitoring.web.mapper.MonitorMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonitorService {

    private final MonitorRepository monitors;
    private final ProjectService projects;
    private final TagService tags;
    private final MonitorMapper mapper;

    /** An absent tag lists the whole project; a given one narrows the page to that label. */
    public Page<MonitorResponse> listByProject(Long projectId, String tag, Pageable pageable) {
        projects.requireExists(projectId);
        Page<Monitor> page = tag == null || tag.isBlank()
                ? monitors.findByProjectId(projectId, pageable)
                : monitors.findByProjectIdAndTagsName(projectId, tag, pageable);
        return page.map(mapper::toResponse);
    }

    public MonitorResponse get(Long id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public MonitorResponse create(Long projectId, MonitorCreateRequest request) {
        Project project = projects.require(projectId);
        if (monitors.existsByProjectIdAndName(projectId, request.name())) {
            throw new ConflictStateException(
                    "Project %d already has a monitor named %s".formatted(projectId, request.name()));
        }

        Monitor monitor = new Monitor();
        monitor.setProject(project);
        monitor.setName(request.name());
        monitor.setUrl(request.url());
        monitor.setHttpMethod(request.httpMethod());
        monitor.setIntervalSec(request.intervalSec());
        monitor.setTimeoutMs(request.timeoutMs());
        monitor.setExpectedStatus(request.expectedStatus());
        monitor.setTags(tags.resolveOrCreate(request.tags()));

        return mapper.toResponse(monitors.save(monitor));
    }

    @Transactional
    public MonitorResponse update(Long id, MonitorUpdateRequest request) {
        Monitor monitor = require(id);
        monitor.setName(request.name());
        monitor.setUrl(request.url());
        monitor.setHttpMethod(request.httpMethod());
        monitor.setIntervalSec(request.intervalSec());
        monitor.setTimeoutMs(request.timeoutMs());
        monitor.setExpectedStatus(request.expectedStatus());
        applyActiveFlag(monitor, request.active());
        return mapper.toResponse(monitor);
    }

    /**
     * Keeps the reported state honest while a monitor is switched off. Leaving it UP would
     * claim the site is fine although nothing checks it any more, and leaving it DOWN would
     * keep an alert standing that nobody is going to resolve.
     */
    private void applyActiveFlag(Monitor monitor, boolean active) {
        monitor.setActive(active);
        if (!active) {
            monitor.setCurrentState(MonitorState.PAUSED);
        } else if (monitor.getCurrentState() == MonitorState.PAUSED) {
            monitor.setCurrentState(MonitorState.UNKNOWN);
        }
    }

    @Transactional
    public MonitorResponse replaceTags(Long id, Set<String> names) {
        Monitor monitor = require(id);
        monitor.setTags(new HashSet<>(tags.resolveOrCreate(names)));
        return mapper.toResponse(monitor);
    }

    @Transactional
    public void delete(Long id) {
        monitors.delete(require(id));
    }

    /** Existence check that does not load the row, for callers that only need the guard. */
    public void requireExists(Long id) {
        if (!monitors.existsById(id)) {
            throw NotFoundException.of("Monitor", id);
        }
    }

    public Monitor require(Long id) {
        return monitors.findById(id).orElseThrow(() -> NotFoundException.of("Monitor", id));
    }
}
