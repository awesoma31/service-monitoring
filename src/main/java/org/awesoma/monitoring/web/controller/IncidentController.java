package org.awesoma.monitoring.web.controller;

import jakarta.validation.Valid;
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
public class IncidentController {

    private final IncidentService incidents;

    /**
     * Check history as an endless feed. The response carries no total count by design —
     * see {@link IncidentService#listResults}.
     */
    @GetMapping("/monitors/{monitorId}/results")
    public Slice<CheckResultResponse> listResults(
            @PathVariable Long monitorId, @Valid PageParams page) {
        return incidents.listResults(monitorId, page.toPageable());
    }

    @GetMapping("/monitors/{monitorId}/incidents")
    public Page<IncidentResponse> listByMonitor(
            @PathVariable Long monitorId,
            @RequestParam(required = false) IncidentStatus status,
            @Valid PageParams page) {
        return incidents.listByMonitor(monitorId, status, page.toPageable());
    }

    @GetMapping("/incidents/{id}")
    public IncidentResponse get(@PathVariable Long id) {
        return incidents.get(id);
    }

    @PostMapping("/incidents/{id}/resolve")
    public IncidentResponse resolve(@PathVariable Long id) {
        return incidents.resolve(id);
    }

    @GetMapping("/incidents/{id}/notifications")
    public Page<NotificationResponse> listNotifications(
            @PathVariable Long id, @Valid PageParams page) {
        return incidents.listNotifications(id, page.toPageable());
    }
}
