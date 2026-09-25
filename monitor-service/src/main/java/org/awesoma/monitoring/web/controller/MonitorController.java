package org.awesoma.monitoring.web.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.service.MonitorService;
import org.awesoma.monitoring.web.dto.common.PageParams;
import org.awesoma.monitoring.web.dto.monitor.MonitorCreateRequest;
import org.awesoma.monitoring.web.dto.monitor.MonitorResponse;
import org.awesoma.monitoring.web.dto.monitor.MonitorTagsRequest;
import org.awesoma.monitoring.web.dto.monitor.MonitorUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Monitors")
public class MonitorController {

    /** Total row count for clients that render page numbers rather than an endless list. */
    private static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    private final MonitorService monitorService;

    @GetMapping("/projects/{projectId}/monitors")
    @Operation(
            summary = "List project monitors",
            description = "Returns one page of monitors, optionally filtered by an exact tag name.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Monitors returned",
                headers = @Header(
                        name = TOTAL_COUNT_HEADER,
                        description = "Total number of monitors matching the filter",
                        schema = @Schema(type = "integer", format = "int64", minimum = "0"))),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public ResponseEntity<Page<MonitorResponse>> listByProject(
            @PathVariable @Positive Long projectId,
            @Parameter(description = "Exact tag name", example = "production")
                    @RequestParam(required = false)
                    String tag,
            @Valid PageParams page) {
        Page<MonitorResponse> monitorPage = monitorService.listByProject(projectId, tag, page.toPageable());
        return ResponseEntity.ok()
                .header(TOTAL_COUNT_HEADER, String.valueOf(monitorPage.getTotalElements()))
                .body(monitorPage);
    }

    @PostMapping("/projects/{projectId}/monitors")
    @Operation(summary = "Create a monitor", description = "Unknown tag names are created automatically.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Monitor created",
                headers = @Header(
                        name = "Location",
                        description = "URI of the created monitor",
                        schema = @Schema(type = "string", format = "uri"))),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public ResponseEntity<MonitorResponse> create(
            @PathVariable @Positive Long projectId,
            @Valid @RequestBody MonitorCreateRequest request) {
        MonitorResponse created = monitorService.create(projectId, request);
        return ResponseEntity.created(URI.create("/api/v1/monitors/" + created.id())).body(created);
    }

    @GetMapping("/monitors/{id}")
    @Operation(summary = "Get a monitor")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Monitor returned"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public ResponseEntity<MonitorResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(monitorService.get(id));
    }

    @PutMapping("/monitors/{id}")
    @Operation(
            summary = "Update a monitor",
            description = "Disabling a monitor pauses it; enabling a paused monitor returns it to UNKNOWN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Monitor updated"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public ResponseEntity<MonitorResponse> update(
            @PathVariable @Positive Long id, @Valid @RequestBody MonitorUpdateRequest request) {
        return ResponseEntity.ok(monitorService.update(id, request));
    }

    @PutMapping("/monitors/{id}/tags")
    @Operation(
            summary = "Replace monitor tags",
            description = "Replaces the complete tag set; unknown tag names are created automatically.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tags replaced"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public ResponseEntity<MonitorResponse> replaceTags(
            @PathVariable @Positive Long id, @Valid @RequestBody MonitorTagsRequest request) {
        return ResponseEntity.ok(monitorService.replaceTags(id, request.tags()));
    }

    @DeleteMapping("/monitors/{id}")
    @Operation(summary = "Delete a monitor")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Monitor deleted"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        monitorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
