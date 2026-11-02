package org.awesoma.report.adapters.out.http;

import feign.FeignException;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.awesoma.integration.reportdata.ReportDataRequests.MonitorSelectionRequest;
import org.awesoma.integration.reportdata.ReportDataRequests.PeriodSearchRequest;
import org.awesoma.integration.reportdata.ReportDataResponses.Page;
import org.awesoma.report.application.port.MonitorPage;
import org.awesoma.report.application.port.ProjectNotFoundException;
import org.awesoma.report.application.port.ReportDataSource;
import org.awesoma.report.application.port.SourcePage;
import org.awesoma.report.application.port.SourceRejectedException;
import org.awesoma.report.application.port.SourceUnavailableException;
import org.awesoma.report.domain.FailureCode;
import org.awesoma.report.domain.MonitorSelection;
import org.awesoma.report.domain.ReportPeriod;
import org.awesoma.report.domain.source.CheckOutcome;
import org.awesoma.report.domain.source.CheckRecord;
import org.awesoma.report.domain.source.IncidentRecord;
import org.awesoma.report.domain.source.IncidentSeverity;
import org.awesoma.report.domain.source.MonitorInfo;
import org.awesoma.report.domain.source.ProjectInfo;
import org.springframework.stereotype.Component;

/**
 * Reads monitoring data over HTTP with report-service's own credentials. A 404 means the
 * project is gone and a 422 that a monitor left it — both are lasting; every other failure,
 * an open circuit included, is a temporary outage worth another attempt.
 */
@Component
@RequiredArgsConstructor
public class HttpReportDataSource implements ReportDataSource {
    private final ReportDataClients.MonitorData monitors;
    private final ReportDataClients.CheckData checks;

    @Override
    public MonitorPage monitors(long projectId, MonitorSelection selection, String cursor, int limit) {
        var page = call(projectId, () -> monitors.selection(projectId, new MonitorSelectionRequest(
                selection.all() ? null : selection.monitorIds(), limit, cursor)));
        var project = page.project() == null ? new ProjectInfo(projectId, null)
                : new ProjectInfo(page.project().id(), page.project().name());
        return new MonitorPage(project, convert(page, item -> new MonitorInfo(item.id(), item.name(), item.url())));
    }

    @Override
    public SourcePage<CheckRecord> checks(long projectId, List<Long> monitorIds, ReportPeriod period, String cursor,
            int limit) {
        var page = call(projectId, () -> checks.checks(new PeriodSearchRequest(projectId, monitorIds,
                period.from(), period.to(), limit, cursor)));
        return convert(page, item -> new CheckRecord(item.id(), item.monitorId(), CheckOutcome.valueOf(item.result()),
                item.responseMs() == null ? null : item.responseMs().longValue(), item.checkedAt()));
    }

    @Override
    public SourcePage<IncidentRecord> incidents(long projectId, List<Long> monitorIds, ReportPeriod period,
            String cursor, int limit) {
        var page = call(projectId, () -> monitors.incidents(projectId, new PeriodSearchRequest(null, monitorIds,
                period.from(), period.to(), limit, cursor)));
        return convert(page, item -> new IncidentRecord(item.id(), item.monitorId(),
                IncidentSeverity.valueOf(item.severity()), item.startedAt(), item.resolvedAt()));
    }

    private static <T> T call(long projectId, Supplier<T> request) {
        try {
            return request.get();
        } catch (RuntimeException error) {
            FeignException response = feignCause(error);
            if (response != null && response.status() == 404) throw new ProjectNotFoundException(projectId);
            if (response != null && response.status() == 422) {
                throw new SourceRejectedException(FailureCode.MONITOR_NOT_IN_PROJECT,
                        "A monitor does not belong to project " + projectId);
            }
            throw new SourceUnavailableException("Report data source is unavailable", error);
        }
    }

    /** A circuit breaker wraps the client's exception; the status is on the cause. */
    private static FeignException feignCause(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof FeignException feign) return feign;
        }
        return null;
    }

    private static <S, T> SourcePage<T> convert(Page<S> page, Function<S, T> item) {
        return new SourcePage<>(page.items().stream().map(item).toList(), page.nextCursor(), page.hasNext(),
                page.sourceCapturedAt(), page.upperId());
    }
}
