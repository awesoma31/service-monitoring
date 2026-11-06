package org.awesoma.check.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Function;
import org.awesoma.check.application.exception.InvalidRequestException;
import org.awesoma.check.application.exception.MonitorNotInProjectException;
import org.awesoma.check.application.exception.ProjectNotFoundException;
import org.awesoma.check.application.port.MonitorOwnership;
import org.awesoma.check.application.port.ReportDataStore;
import org.awesoma.integration.reportdata.ReportDataCursor;
import org.awesoma.integration.reportdata.ReportDataRequests.PeriodSearchRequest;
import org.awesoma.integration.reportdata.ReportDataResponses.CheckItem;
import org.awesoma.integration.reportdata.ReportDataResponses.Page;
import reactor.core.publisher.Mono;

/** Check history for report generation, read page by page under the shared report-data contract. */
public class ReportDataService {
    private final ReportDataStore data;
    private final MonitorOwnership monitors;
    private final Clock clock;

    public ReportDataService(ReportDataStore data, MonitorOwnership monitors, Clock clock) {
        this.data = data;
        this.monitors = monitors;
        this.clock = clock;
    }

    public Mono<Page<CheckItem>> checks(PeriodSearchRequest request) {
        return Mono.fromRunnable(() -> validate(request))
                .then(Mono.defer(() -> monitors.ownership(request.projectId(), request.monitorIds())))
                .flatMap(answer -> switch (answer) {
                    case OWNED -> position(request);
                    case PROJECT_NOT_FOUND -> Mono.<ReportDataCursor>error(
                            new ProjectNotFoundException(request.projectId()));
                    case MONITOR_NOT_IN_PROJECT -> Mono.<ReportDataCursor>error(
                            new MonitorNotInProjectException(request.projectId()));
                })
                .flatMap(cursor -> data.find(request, cursor).collectList().map(rows -> Page.of(rows, request.limit(),
                        cursor, scope(request), CheckItem::id, Function.identity(), null)));
    }

    private Mono<ReportDataCursor> position(PeriodSearchRequest request) {
        if (request.cursor() != null) {
            try {
                return Mono.just(ReportDataCursor.decode(request.cursor(), scope(request)));
            } catch (IllegalArgumentException invalid) {
                return Mono.error(new InvalidRequestException(invalid.getMessage()));
            }
        }
        return data.upperId().map(upper -> ReportDataCursor.start(upper, Instant.now(clock)));
    }

    private static String scope(PeriodSearchRequest request) {
        return request.scope("checks", request.projectId());
    }

    private static void validate(PeriodSearchRequest request) {
        if (request.projectId() == null) throw new InvalidRequestException("Project id is required");
        try {
            request.validate();
        } catch (IllegalArgumentException invalid) {
            throw new InvalidRequestException(invalid.getMessage());
        }
    }
}
