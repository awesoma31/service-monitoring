package org.awesoma.check.client;

import org.awesoma.integration.reportdata.ReportDataRequests.MonitorOwnershipRequest;
import org.awesoma.integration.reportdata.ReportDataResponses.OwnershipResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * check-service keeps no projects, so it asks monitor-service whether the monitors of a
 * report request belong to the project, rather than reading another service's database.
 */
@FeignClient(name = "monitor-service", contextId = "reportDataMonitorClient", path = "/internal/report-data")
public interface ReportDataMonitorClient {

    @PostMapping("/projects/{projectId}/monitors/ownership")
    OwnershipResponse ownership(@PathVariable("projectId") Long projectId, @RequestBody MonitorOwnershipRequest request);
}
