package org.awesoma.report.adapters.out.http;

import org.awesoma.integration.reportdata.ReportDataRequests.MonitorSelectionRequest;
import org.awesoma.integration.reportdata.ReportDataRequests.PeriodSearchRequest;
import org.awesoma.integration.reportdata.ReportDataResponses.CheckItem;
import org.awesoma.integration.reportdata.ReportDataResponses.IncidentItem;
import org.awesoma.integration.reportdata.ReportDataResponses.MonitorItem;
import org.awesoma.integration.reportdata.ReportDataResponses.Page;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** The internal report-data APIs of the services that own monitoring data. */
public final class ReportDataClients {

    private ReportDataClients() {
    }

    @FeignClient(name = "monitor-service", contextId = "monitorReportDataClient", path = "/internal/report-data")
    public interface MonitorData {

        @PostMapping("/projects/{projectId}/selection")
        Page<MonitorItem> selection(@PathVariable("projectId") Long projectId,
                @RequestBody MonitorSelectionRequest request);

        @PostMapping("/projects/{projectId}/incidents/search")
        Page<IncidentItem> incidents(@PathVariable("projectId") Long projectId,
                @RequestBody PeriodSearchRequest request);
    }

    @FeignClient(name = "check-service", contextId = "checkReportDataClient", path = "/internal/report-data")
    public interface CheckData {

        @PostMapping("/checks/search")
        Page<CheckItem> checks(@RequestBody PeriodSearchRequest request);
    }
}
