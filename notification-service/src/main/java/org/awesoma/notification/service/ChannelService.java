package org.awesoma.notification.service;

import lombok.RequiredArgsConstructor;
import org.awesoma.notification.client.ReactiveMonitorClient;
import org.awesoma.notification.domain.Channel;
import org.awesoma.notification.repository.ChannelRepository;
import org.awesoma.notification.support.JpaExecutor;
import org.awesoma.notification.web.dto.ChannelCreateRequest;
import org.awesoma.notification.web.dto.ChannelResponse;
import org.awesoma.notification.web.dto.ChannelUpdateRequest;
import org.awesoma.notification.web.exception.ConflictStateException;
import org.awesoma.notification.web.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository channels;
    private final ReactiveMonitorClient monitorService;
    private final JpaExecutor jpa;

    public Mono<Page<ChannelResponse>> listByProject(Long projectId, Pageable pageable) {
        return requireProject(projectId).then(jpa.read(() ->
                channels.findByProjectId(projectId, pageable).map(ChannelResponse::of)));
    }

    public Mono<ChannelResponse> get(Long id) {
        return jpa.read(() -> ChannelResponse.of(require(id)));
    }

    public Mono<ChannelResponse> create(Long projectId, ChannelCreateRequest request) {
        return requireProject(projectId).then(jpa.write(() -> {
            if (channels.existsByProjectIdAndTypeAndTarget(projectId, request.type(), request.target())) {
                throw new ConflictStateException("Project %d already notifies %s at %s"
                        .formatted(projectId, request.type(), request.target()));
            }
            Channel channel = new Channel();
            channel.setProjectId(projectId);
            channel.setType(request.type());
            channel.setTarget(request.target());
            return ChannelResponse.of(channels.save(channel));
        }));
    }

    public Mono<ChannelResponse> update(Long id, ChannelUpdateRequest request) {
        return jpa.write(() -> {
            Channel channel = require(id);
            channel.setTarget(request.target());
            channel.setEnabled(request.enabled());
            return ChannelResponse.of(channel);
        });
    }

    public Mono<Void> delete(Long id) {
        return jpa.write(() -> {
            channels.delete(require(id));
            return null;
        });
    }

    /** Called by monitor-service when a project is deleted; notifications cascade. */
    public Mono<Integer> deleteByProject(Long projectId) {
        return jpa.write(() -> channels.deleteByProjectId(projectId));
    }

    private Mono<Void> requireProject(Long projectId) {
        return monitorService.projectExists(projectId).flatMap(exists -> exists
                ? Mono.<Void>empty()
                : Mono.error(new NotFoundException("Project %d not found".formatted(projectId))));
    }

    private Channel require(Long id) {
        return channels.findById(id)
                .orElseThrow(() -> new NotFoundException("Channel %d not found".formatted(id)));
    }
}
