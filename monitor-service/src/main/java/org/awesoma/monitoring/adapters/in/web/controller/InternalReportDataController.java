package org.awesoma.monitoring.adapters.in.web.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.awesoma.integration.reportdata.ReportDataRequests.MonitorOwnershipRequest;
import org.awesoma.integration.reportdata.ReportDataRequests.MonitorSelectionRequest;
import org.awesoma.integration.reportdata.ReportDataRequests.PeriodSearchRequest;
import org.awesoma.integration.reportdata.ReportDataResponses.IncidentItem;
import org.awesoma.integration.reportdata.ReportDataResponses.MonitorItem;
import org.awesoma.integration.reportdata.ReportDataResponses.OwnershipResponse;
import org.awesoma.integration.reportdata.ReportDataResponses.Page;
import org.awesoma.monitoring.application.usecase.ReportDataService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Data for report generation, for services with service credentials only. Like the rest of
 * /internal/**, it is not routed by the gateway and stays out of the API docs.
 */
@Hidden
@Validated
@RestController
@RequestMapping("/internal/report-data/projects/{projectId}")
@RequiredArgsConstructor
public class InternalReportDataController {
    private final ReportDataService reportData;

    /** The project and a page of its monitors; every listed ID must belong to it. */
    @PostMapping("/selection")
    public Page<MonitorItem> selection(@PathVariable @Positive long projectId,
            @RequestBody MonitorSelectionRequest request) {
        return reportData.monitors(projectId, request);
    }

    /** Lets check-service, which keeps no projects, check a selection against its owner. */
    @PostMapping("/monitors/ownership")
    public OwnershipResponse ownership(@PathVariable @Positive long projectId,
            @RequestBody MonitorOwnershipRequest request) {
        return new OwnershipResponse(reportData.ownership(projectId, request));
    }

    @PostMapping("/incidents/search")
    public Page<IncidentItem> incidents(@PathVariable @Positive long projectId,
            @RequestBody PeriodSearchRequest request) {
        return reportData.incidents(projectId, request);
    }
}
