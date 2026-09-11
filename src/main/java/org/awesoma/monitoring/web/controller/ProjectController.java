package org.awesoma.monitoring.web.controller;

import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.service.ProjectService;
import org.awesoma.monitoring.web.dto.common.PageParams;
import org.awesoma.monitoring.web.dto.project.ProjectCreateRequest;
import org.awesoma.monitoring.web.dto.project.ProjectMemberRequest;
import org.awesoma.monitoring.web.dto.project.ProjectMemberResponse;
import org.awesoma.monitoring.web.dto.project.ProjectMemberRoleRequest;
import org.awesoma.monitoring.web.dto.project.ProjectResponse;
import org.awesoma.monitoring.web.dto.project.ProjectUpdateRequest;
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
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projects;

    @GetMapping
    public Page<ProjectResponse> list(@Valid PageParams page) {
        return projects.list(page.toPageable());
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable Long id) {
        return projects.get(id);
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectCreateRequest request) {
        ProjectResponse created = projects.create(request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(
            @PathVariable Long id, @Valid @RequestBody ProjectUpdateRequest request) {
        return projects.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        projects.delete(id);
    }

    @GetMapping("/{id}/members")
    public Page<ProjectMemberResponse> listMembers(@PathVariable Long id, @Valid PageParams page) {
        return projects.listMembers(id, page.toPageable());
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ProjectMemberResponse> addMember(
            @PathVariable Long id, @Valid @RequestBody ProjectMemberRequest request) {
        ProjectMemberResponse created = projects.addMember(id, request);
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + id + "/members/" + created.userId()))
                .body(created);
    }

    @PutMapping("/{id}/members/{userId}")
    public ProjectMemberResponse changeRole(
            @PathVariable Long id,
            @PathVariable Long userId,
            @Valid @RequestBody ProjectMemberRoleRequest request) {
        return projects.changeRole(id, userId, request.role());
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long id, @PathVariable Long userId) {
        projects.removeMember(id, userId);
    }
}
