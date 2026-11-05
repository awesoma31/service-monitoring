package org.awesoma.notification.application.usecase;

import org.awesoma.notification.application.exception.ConflictStateException;
import org.awesoma.notification.application.exception.NotFoundException;
import org.awesoma.notification.application.model.PageResult;
import org.awesoma.notification.application.port.ChannelStore;
import org.awesoma.notification.application.port.MonitorDirectory;
import org.awesoma.notification.application.port.ProjectGuard;
import org.awesoma.notification.application.port.UnitOfWork;
import org.awesoma.notification.domain.Channel;
import org.awesoma.notification.domain.ChannelType;
import reactor.core.publisher.Mono;

public class ChannelService {
    private final ChannelStore channels;
    private final MonitorDirectory monitors;
    private final UnitOfWork work;
    private final ProjectGuard projects;

    public ChannelService(ChannelStore channels, MonitorDirectory monitors, UnitOfWork work, ProjectGuard projects) {
        this.channels = channels;
        this.monitors = monitors;
        this.work = work;
        this.projects = projects;
    }

    public Mono<PageResult<Channel>> listByProject(Long projectId, int page, int size) {
        return requireProject(projectId).then(work.read(() -> channels.pageByProject(projectId, page, size)));
    }

    public Mono<Channel> get(Long id) {
        return work.read(() -> require(id));
    }

    public Mono<Channel> create(Long projectId, ChannelType type, String target) {
        return requireProject(projectId).then(work.write(() -> {
            projects.lock(projectId);
            if (projects.deleted(projectId)) {
                throw new NotFoundException("Project %d not found".formatted(projectId));
            }
            if (channels.existsByProjectIdAndTypeAndTarget(projectId, type, target)) {
                throw new ConflictStateException("Project %d already notifies %s at %s"
                        .formatted(projectId, type, target));
            }
            Channel channel = new Channel();
            channel.setProjectId(projectId);
            channel.setType(type);
            channel.setTarget(target);
            return channels.save(channel);
        }));
    }

    public Mono<Channel> update(Long id, String target, boolean enabled) {
        return work.write(() -> {
            Channel channel = require(id);
            channel.setTarget(target);
            channel.setEnabled(enabled);
            return channels.save(channel);
        });
    }

    public Mono<Void> delete(Long id) {
        return work.write(() -> {
            channels.delete(require(id));
            return null;
        });
    }

    public Mono<Integer> deleteByProject(Long projectId) {
        return work.write(() -> deleteByProjectInTransaction(projectId));
    }

    /** Called inside the same unit of work as the event inbox. */
    public int deleteByProjectInTransaction(Long projectId) {
        projects.lock(projectId);
        projects.markDeleted(projectId);
        return channels.deleteByProjectId(projectId);
    }

    private Mono<Void> requireProject(Long id) {
        return monitors.projectExists(id).flatMap(exists -> exists ? Mono.empty()
                : Mono.error(new NotFoundException("Project %d not found".formatted(id))));
    }

    private Channel require(Long id) {
        return channels.findById(id)
                .orElseThrow(() -> new NotFoundException("Channel %d not found".formatted(id)));
    }
}
