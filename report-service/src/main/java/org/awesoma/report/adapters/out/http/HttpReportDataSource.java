package org.awesoma.report.adapters.out.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.integration.reportdata.ReportDataRequests.MonitorSelectionRequest;
import org.awesoma.integration.reportdata.ReportDataRequests.PeriodSearchRequest;
import org.awesoma.integration.reportdata.ReportDataResponses;
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
 * Reads monitoring data over HTTP with report-service's own credentials. Only a refusal that
 * names its reason — the project is gone, or a monitor left it — is lasting; any other failure,
 * a bare 404 from a wrong route or an open circuit included, is an outage worth another attempt.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HttpReportDataSource implements ReportDataSource {
    private final ReportDataClients.MonitorData monitors;
    private final ReportDataClients.CheckData checks;
    private final ObjectMapper json;

    @Override
    public MonitorPage monitors(long projectId, MonitorSelection selection, String cursor, int limit) {
        var page = call(projectId, "monitor-service", "monitors", () -> monitors.selection(projectId, new MonitorSelectionRequest(
                selection.all() ? null : selection.monitorIds(), limit, cursor)));
        var project = page.project() == null ? new ProjectInfo(projectId, null)
                : new ProjectInfo(page.project().id(), page.project().name());
        return new MonitorPage(project, convert(page, item -> new MonitorInfo(item.id(), item.name(), item.url())));
    }

    @Override
    public SourcePage<CheckRecord> checks(long projectId, List<Long> monitorIds, ReportPeriod period, String cursor,
            int limit) {
        var page = call(projectId, "check-service", "checks", () -> checks.checks(new PeriodSearchRequest(projectId, monitorIds,
                period.from(), period.to(), limit, cursor)));
        return convert(page, item -> new CheckRecord(item.id(), item.monitorId(), CheckOutcome.valueOf(item.result()),
                item.responseMs() == null ? null : item.responseMs().longValue(), item.checkedAt()));
    }

    @Override
    public SourcePage<IncidentRecord> incidents(long projectId, List<Long> monitorIds, ReportPeriod period,
            String cursor, int limit) {
        var page = call(projectId, "monitor-service", "incidents", () -> monitors.incidents(projectId, new PeriodSearchRequest(null, monitorIds,
                period.from(), period.to(), limit, cursor)));
        return convert(page, item -> new IncidentRecord(item.id(), item.monitorId(),
                IncidentSeverity.valueOf(item.severity()), item.startedAt(), item.resolvedAt()));
    }

    private <T> T call(long projectId, String service, String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RuntimeException error) {
            log.warn("Report data request failed: service={} operation={} project_id={} error_type={}",
                    service, operation, projectId, error.getClass().getSimpleName());
            switch (refusalCode(error)) {
                case ReportDataResponses.PROJECT_NOT_FOUND -> throw new ProjectNotFoundException(projectId);
                case ReportDataResponses.MONITOR_NOT_IN_PROJECT -> throw new SourceRejectedException(
                        FailureCode.MONITOR_NOT_IN_PROJECT, "A monitor does not belong to project " + projectId);
                default -> throw new SourceUnavailableException("Report data source is unavailable", error);
            }
        }
    }

    /** The {@code code} of a problem body, or an empty string when there is none. */
    private String refusalCode(RuntimeException error) {
        FeignException response = feignCause(error);
        if (response == null || response.status() < 400 || response.status() >= 500) return "";
        try {
            return json.readTree(response.contentUTF8()).path("code").asText("");
        } catch (JsonProcessingException | RuntimeException unreadable) {
            return "";
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
