package org.awesoma.monitoring.web.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.service.ProjectService;
import org.awesoma.monitoring.web.dto.common.PageParams;
import org.awesoma.monitoring.web.dto.project.ProjectCreateRequest;
import org.awesoma.monitoring.web.dto.project.ProjectMemberRequest;
import org.awesoma.monitoring.web.dto.project.ProjectMemberResponse;
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
@Tag(name = "Projects")
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    @Operation(summary = "List projects", description = "Returns one page of projects, at most 50 records.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Projects returned"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    })
    public Page<ProjectResponse> list(@Valid PageParams page) {
        return projectService.list(page.toPageable());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a project")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Project returned"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public ProjectResponse get(@PathVariable @Positive Long id) {
        return projectService.get(id);
    }

    @PostMapping
    @Operation(
            summary = "Create a project",
            description = "Creates the project and adds its owner as an OWNER member atomically.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Project created",
                headers = @Header(
                        name = "Location",
                        description = "URI of the created project",
                        schema = @Schema(type = "string", format = "uri"))),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectCreateRequest request) {
        ProjectResponse created = projectService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a project", description = "Changes the project name; its slug remains fixed.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Project updated"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public ProjectResponse update(
            @PathVariable @Positive Long id, @Valid @RequestBody ProjectUpdateRequest request) {
        return projectService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a project")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Project deleted"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public void delete(@PathVariable @Positive Long id) {
        projectService.delete(id);
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "List project members")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Members returned"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public Page<ProjectMemberResponse> listMembers(
            @PathVariable @Positive Long id, @Valid PageParams page) {
        return projectService.listMembers(id, page.toPageable());
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Add a project member")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Member added",
                headers = @Header(
                        name = "Location",
                        description = "URI of the new project membership",
                        schema = @Schema(type = "string", format = "uri"))),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public ResponseEntity<ProjectMemberResponse> addMember(
            @PathVariable @Positive Long id,
            @Valid @RequestBody ProjectMemberRequest request) {
        ProjectMemberResponse created = projectService.addMember(id, request);
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + id + "/members/" + created.userId()))
                .body(created);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Remove a project member")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Member removed"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public void removeMember(
            @PathVariable @Positive Long id, @PathVariable @Positive Long userId) {
        projectService.removeMember(id, userId);
    }
}
