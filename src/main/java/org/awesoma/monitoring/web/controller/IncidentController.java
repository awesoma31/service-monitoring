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
import org.awesoma.monitoring.web.dto.incident.CheckResultResponse;
import org.awesoma.monitoring.web.dto.incident.IncidentResponse;
import org.awesoma.monitoring.web.dto.incident.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
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

    private final IncidentService incidents;

    /**
     * Check history as an endless feed. The response carries no total count by design —
     * see {@link IncidentService#listResults}.
     */
    @GetMapping("/monitors/{monitorId}/results")
    @Operation(
            summary = "Scroll monitor check history",
            description = "Returns a Slice ordered from newest to oldest. The response intentionally "
                    + "contains hasNext but no total count, so clients can implement infinite scrolling "
                    + "without an additional count query.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Check-result slice returned"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public Slice<CheckResultResponse> listResults(
            @PathVariable @Positive Long monitorId, @Valid PageParams page) {
        return incidents.listResults(monitorId, page.toPageable());
    }

    @GetMapping("/monitors/{monitorId}/incidents")
    @Operation(
            summary = "List monitor incidents",
            description = "Returns one page, optionally filtered by incident status.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Incidents returned"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public Page<IncidentResponse> listByMonitor(
            @PathVariable @Positive Long monitorId,
            @Parameter(description = "Optional incident status filter", example = "OPEN")
                    @RequestParam(required = false)
                    IncidentStatus status,
            @Valid PageParams page) {
        return incidents.listByMonitor(monitorId, status, page.toPageable());
    }

    @GetMapping("/incidents/{id}")
    @Operation(summary = "Get an incident")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Incident returned"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public IncidentResponse get(@PathVariable @Positive Long id) {
        return incidents.get(id);
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
    public IncidentResponse resolve(@PathVariable @Positive Long id) {
        return incidents.resolve(id);
    }

    @GetMapping("/incidents/{id}/notifications")
    @Operation(summary = "List notifications created for an incident")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notifications returned"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public Page<NotificationResponse> listNotifications(
            @PathVariable @Positive Long id, @Valid PageParams page) {
        return incidents.listNotifications(id, page.toPageable());
    }
}
