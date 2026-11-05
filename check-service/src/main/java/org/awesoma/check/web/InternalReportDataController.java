package org.awesoma.check.web;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.awesoma.check.application.usecase.ReportDataService;
import org.awesoma.integration.reportdata.ReportDataRequests.PeriodSearchRequest;
import org.awesoma.integration.reportdata.ReportDataResponses.CheckItem;
import org.awesoma.integration.reportdata.ReportDataResponses.Page;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Check history for report generation, for services with service credentials only. Like the
 * rest of /internal/**, it is not routed by the gateway and stays out of the API docs.
 */
@Hidden
@RestController
@RequiredArgsConstructor
public class InternalReportDataController {
    private final ReportDataService reportData;

    @PostMapping("/internal/report-data/checks/search")
    public Mono<Page<CheckItem>> checks(@RequestBody PeriodSearchRequest request) {
        return reportData.checks(new org.awesoma.check.application.model.ReportQuery(request.projectId(),
                request.monitorIds(), request.from(), request.to(), request.limit(), request.cursor()))
                .map(page -> new Page<>(page.items().stream().map(item -> new CheckItem(item.id(), item.monitorId(),
                        item.result(), item.responseMs(), item.checkedAt())).toList(), page.nextCursor(),
                        page.hasNext(), page.sourceCapturedAt(), page.upperId(), null));
    }
}
