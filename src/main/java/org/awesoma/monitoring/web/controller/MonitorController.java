package org.awesoma.monitoring.web.controller;

import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.service.MonitorService;
import org.awesoma.monitoring.web.dto.common.PageParams;
import org.awesoma.monitoring.web.dto.monitor.MonitorCreateRequest;
import org.awesoma.monitoring.web.dto.monitor.MonitorResponse;
import org.awesoma.monitoring.web.dto.monitor.MonitorTagsRequest;
import org.awesoma.monitoring.web.dto.monitor.MonitorUpdateRequest;
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
public class MonitorController {

    /** Total row count for clients that render page numbers rather than an endless list. */
    private static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    private final MonitorService monitors;

    @GetMapping("/projects/{projectId}/monitors")
    public ResponseEntity<Page<MonitorResponse>> listByProject(
            @PathVariable Long projectId, @Valid PageParams page) {
        Page<MonitorResponse> monitorPage = monitors.listByProject(projectId, page.toPageable());
        return ResponseEntity.ok()
                .header(TOTAL_COUNT_HEADER, String.valueOf(monitorPage.getTotalElements()))
                .body(monitorPage);
    }

    @PostMapping("/projects/{projectId}/monitors")
    public ResponseEntity<MonitorResponse> create(
            @PathVariable Long projectId, @Valid @RequestBody MonitorCreateRequest request) {
        MonitorResponse created = monitors.create(projectId, request);
        return ResponseEntity.created(URI.create("/api/v1/monitors/" + created.id())).body(created);
    }

    @GetMapping("/monitors/{id}")
    public MonitorResponse get(@PathVariable Long id) {
        return monitors.get(id);
    }

    @PutMapping("/monitors/{id}")
    public MonitorResponse update(
            @PathVariable Long id, @Valid @RequestBody MonitorUpdateRequest request) {
        return monitors.update(id, request);
    }

    @PutMapping("/monitors/{id}/tags")
    public MonitorResponse replaceTags(
            @PathVariable Long id, @Valid @RequestBody MonitorTagsRequest request) {
        return monitors.replaceTags(id, request.tags());
    }

    @DeleteMapping("/monitors/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        monitors.delete(id);
    }
}
