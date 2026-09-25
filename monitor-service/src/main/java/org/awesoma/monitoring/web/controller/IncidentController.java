package org.awesoma.monitoring.web.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.awesoma.monitoring.service.IncidentService;
import org.awesoma.monitoring.web.dto.common.PageParams;
import org.awesoma.monitoring.web.dto.incident.IncidentResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Incidents")
public class IncidentController {

    private final IncidentService incidentService;

    @GetMapping("/monitors/{monitorId}/incidents")
    @Operation(
            summary = "List monitor incidents",
            description = "Returns one page, optionally filtered by incident status.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Incidents returned"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public ResponseEntity<Page<IncidentResponse>> listByMonitor(
            @PathVariable @Positive Long monitorId,
            @Parameter(description = "Optional incident status filter", example = "OPEN")
                    @RequestParam(required = false)
                    IncidentStatus status,
            @Valid PageParams page) {
        return ResponseEntity.ok(incidentService.listByMonitor(monitorId, status, page.toPageable()));
    }

    @GetMapping("/incidents/{id}")
    @Operation(summary = "Get an incident")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Incident returned"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public ResponseEntity<IncidentResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(incidentService.get(id));
    }

    @PostMapping("/incidents/{id}/resolve")
    @Operation(
            summary = "Resolve an incident manually",
            description = "Marks the incident resolved and returns a DOWN monitor to UNKNOWN until its next check.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Incident resolved"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public ResponseEntity<IncidentResponse> resolve(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(incidentService.resolve(id));
    }

}
