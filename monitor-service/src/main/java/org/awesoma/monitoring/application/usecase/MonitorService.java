package org.awesoma.monitoring.application.usecase;

import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.awesoma.monitoring.application.port.MonitorRepositoryPort;
import org.awesoma.monitoring.application.port.command.MonitorCreateRequest;
import org.awesoma.monitoring.application.model.view.MonitorResponse;
import org.awesoma.monitoring.application.port.command.MonitorUpdateRequest;
import org.awesoma.monitoring.application.exception.ConflictStateException;
import org.awesoma.monitoring.application.exception.NotFoundException;
import org.awesoma.monitoring.application.port.MonitorPresenter;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;

import java.util.Set;
import org.awesoma.monitoring.application.event.MonitorDeleted;
import org.awesoma.monitoring.application.port.DomainEvents;

@RequiredArgsConstructor
public class MonitorService {

    private final MonitorRepositoryPort monitors;
    private final ProjectService projects;
    private final TagService tags;
    private final MonitorPresenter mapper;
    private final DomainEvents events;

    /** An absent tag lists the whole project; a given one narrows the page to that label. */
    public Page<MonitorResponse> listByProject(Long projectId, String tag, PageRequest pageable) {
        projects.requireExists(projectId);
        Page<Monitor> page = tag == null || tag.isBlank()
                ? monitors.findByProjectId(projectId, pageable)
                : monitors.findByProjectIdAndTagsName(projectId, tag, pageable);
        return page.map(mapper::toResponse);
    }

    public MonitorResponse get(Long id) {
        return mapper.toResponse(require(id));
    }

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

    public MonitorResponse update(Long id, MonitorUpdateRequest request) {
        // Serializes configuration changes with an in-flight check outcome. Whichever gets
        // the row first wins cleanly: an update clears the claim, so a later old outcome is ignored.
        Monitor monitor = monitors.findWithLockById(id)
                .orElseThrow(() -> NotFoundException.of("Monitor", id));
        Long projectId = monitor.getProject().getId();
        if (monitors.existsByProjectIdAndNameAndIdNot(projectId, request.name(), id)) {
            throw new ConflictStateException(
                    "Project %d already has a monitor named %s".formatted(projectId, request.name()));
        }
        monitor.setName(request.name());
        monitor.setUrl(request.url());
        monitor.setHttpMethod(request.httpMethod());
        monitor.setIntervalSec(request.intervalSec());
        monitor.setTimeoutMs(request.timeoutMs());
        monitor.setExpectedStatus(request.expectedStatus());
        monitor.clearCheckClaim();
        applyActiveFlag(monitor, request.active());
        return mapper.toResponse(monitors.save(monitor));
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

    public MonitorResponse replaceTags(Long id, Set<String> names) {
        Monitor monitor = require(id);
        monitor.setTags(new HashSet<>(tags.resolveOrCreate(names)));
        return mapper.toResponse(monitors.save(monitor));
    }

    public void delete(Long id) {
        monitors.delete(require(id));
        events.publish(new MonitorDeleted(id));
    }

    public boolean exists(Long id) {
        return monitors.existsById(id);
    }

    /** Existence check that does not load the row, for callers that only need the guard. */
    public void requireExists(Long id) {
        if (!monitors.existsById(id)) {
            throw NotFoundException.of("Monitor", id);
        }
    }

    public void save(Monitor monitor) { monitors.save(monitor); }

    public Monitor require(Long id) {
        return monitors.findById(id).orElseThrow(() -> NotFoundException.of("Monitor", id));
    }
}
