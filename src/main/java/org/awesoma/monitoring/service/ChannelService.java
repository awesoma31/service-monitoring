package org.awesoma.monitoring.service;

import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.domain.entity.Channel;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.repository.ChannelRepository;
import org.awesoma.monitoring.web.dto.channel.ChannelCreateRequest;
import org.awesoma.monitoring.web.dto.channel.ChannelResponse;
import org.awesoma.monitoring.web.dto.channel.ChannelUpdateRequest;
import org.awesoma.monitoring.web.exception.ConflictStateException;
import org.awesoma.monitoring.web.exception.NotFoundException;
import org.awesoma.monitoring.web.mapper.ChannelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChannelService {

    private final ChannelRepository channels;
    private final ProjectService projects;
    private final ChannelMapper mapper;

    public Page<ChannelResponse> listByProject(Long projectId, Pageable pageable) {
        projects.requireExists(projectId);
        return channels.findByProjectId(projectId, pageable).map(mapper::toResponse);
    }

    public ChannelResponse get(Long id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public ChannelResponse create(Long projectId, ChannelCreateRequest request) {
        Project project = projects.require(projectId);
        if (channels.existsByProjectIdAndTypeAndTarget(projectId, request.type(), request.target())) {
            throw new ConflictStateException(
                    "Project %d already notifies %s at %s"
                            .formatted(projectId, request.type(), request.target()));
        }
        Channel channel = new Channel();
        channel.setProject(project);
        channel.setType(request.type());
        channel.setTarget(request.target());
        return mapper.toResponse(channels.save(channel));
    }

    @Transactional
    public ChannelResponse update(Long id, ChannelUpdateRequest request) {
        Channel channel = require(id);
        channel.setTarget(request.target());
        channel.setEnabled(request.enabled());
        return mapper.toResponse(channel);
    }

    @Transactional
    public void delete(Long id) {
        channels.delete(require(id));
    }

    private Channel require(Long id) {
        return channels.findById(id).orElseThrow(() -> NotFoundException.of("Channel", id));
    }
}
