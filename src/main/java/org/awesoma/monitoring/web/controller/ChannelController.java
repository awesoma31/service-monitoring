package org.awesoma.monitoring.web.controller;

import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.service.ChannelService;
import org.awesoma.monitoring.web.dto.channel.ChannelCreateRequest;
import org.awesoma.monitoring.web.dto.channel.ChannelResponse;
import org.awesoma.monitoring.web.dto.channel.ChannelUpdateRequest;
import org.awesoma.monitoring.web.dto.common.PageParams;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channels;

    @GetMapping("/projects/{projectId}/channels")
    public Page<ChannelResponse> listByProject(
            @PathVariable Long projectId, @Valid PageParams page) {
        return channels.listByProject(projectId, page.toPageable());
    }

    @PostMapping("/projects/{projectId}/channels")
    public ResponseEntity<ChannelResponse> create(
            @PathVariable Long projectId, @Valid @RequestBody ChannelCreateRequest request) {
        ChannelResponse created = channels.create(projectId, request);
        return ResponseEntity.created(URI.create("/api/v1/channels/" + created.id())).body(created);
    }

    @GetMapping("/channels/{id}")
    public ChannelResponse get(@PathVariable Long id) {
        return channels.get(id);
    }

    @PutMapping("/channels/{id}")
    public ChannelResponse update(
            @PathVariable Long id, @Valid @RequestBody ChannelUpdateRequest request) {
        return channels.update(id, request);
    }

    @DeleteMapping("/channels/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        channels.delete(id);
    }
}
