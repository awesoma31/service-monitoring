package org.awesoma.check.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.awesoma.check.client.ReportDataMonitorClient;
import org.awesoma.check.web.exception.InvalidRequestException;
import org.awesoma.check.web.exception.MonitorNotInProjectException;
import org.awesoma.check.web.exception.NotFoundException;
import org.awesoma.check.web.exception.ServiceUnavailableException;
import org.awesoma.integration.reportdata.ReportDataCursor;
import org.awesoma.integration.reportdata.ReportDataRequests.MonitorOwnershipRequest;
import org.awesoma.integration.reportdata.ReportDataRequests.PeriodSearchRequest;
import org.awesoma.integration.reportdata.ReportDataResponses.CheckItem;
import org.awesoma.integration.reportdata.ReportDataResponses.Page;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Checks of the selected monitors with {@code from <= checked_at < to}, page by page in ID
 * order up to the highest ID that existed when the walk started. A check written later, even
 * one dated inside the period, is left out of the walk.
 */
@Service
@RequiredArgsConstructor
public class ReportDataService {
    private final DatabaseClient db;
    private final ReportDataMonitorClient monitorService;
    private final Clock clock = Clock.systemUTC();

    public Mono<Page<CheckItem>> checks(PeriodSearchRequest request) {
        return Mono.fromRunnable(() -> validate(request))
                .then(Mono.defer(() -> requireOwned(request.projectId(), request.monitorIds())))
                .then(Mono.defer(() -> position(request)))
                .flatMap(cursor -> db.sql("""
                                SELECT id, monitor_id, result, response_ms, checked_at FROM check_results
                                WHERE monitor_id = ANY(:monitorIds)
                                  AND checked_at >= :from AND checked_at < :to
                                  AND id > :after AND id <= :upper
                                ORDER BY id
                                LIMIT :limit
                                """)
                        .bind("monitorIds", request.monitorIds().toArray(Long[]::new))
                        .bind("from", request.from().atOffset(ZoneOffset.UTC))
                        .bind("to", request.to().atOffset(ZoneOffset.UTC))
                        .bind("after", cursor.lastId())
                        .bind("upper", cursor.upperId())
                        .bind("limit", request.limit() + 1)
                        .map(row -> new CheckItem(row.get("id", Long.class), row.get("monitor_id", Long.class),
                                row.get("result", String.class), row.get("response_ms", Integer.class),
                                row.get("checked_at", OffsetDateTime.class).toInstant()))
                        .all()
                        .collectList()
                        .map(rows -> page(rows, request, cursor)));
    }

    private Mono<Void> requireOwned(long projectId, List<Long> monitorIds) {
        return Mono.fromCallable(() -> monitorService.ownership(projectId, new MonitorOwnershipRequest(monitorIds)))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(error -> new ServiceUnavailableException("monitor-service", error))
                .flatMap(answer -> switch (answer.ownership()) {
                    case OWNED -> Mono.empty();
                    case PROJECT_NOT_FOUND -> Mono.error(NotFoundException.of("Project", projectId));
                    case MONITOR_NOT_IN_PROJECT -> Mono.error(new MonitorNotInProjectException(projectId));
                });
    }

    private Mono<ReportDataCursor> position(PeriodSearchRequest request) {
        String scope = request.scope("checks", request.projectId());
        if (request.cursor() != null) {
            try {
                return Mono.just(ReportDataCursor.decode(request.cursor(), scope));
            } catch (IllegalArgumentException invalid) {
                return Mono.error(new InvalidRequestException(invalid.getMessage()));
            }
        }
        return db.sql("SELECT COALESCE(MAX(id), 0) AS upper FROM check_results")
                .map(row -> row.get("upper", Long.class))
                .one()
                .map(upper -> ReportDataCursor.start(upper, Instant.now(clock)));
    }

    private static Page<CheckItem> page(List<CheckItem> rows, PeriodSearchRequest request, ReportDataCursor cursor) {
        boolean hasNext = rows.size() > request.limit();
        var visible = hasNext ? rows.subList(0, request.limit()) : rows;
        String next = hasNext
                ? cursor.after(visible.get(visible.size() - 1).id()).encode(request.scope("checks", request.projectId()))
                : null;
        return new Page<>(visible, next, hasNext, cursor.capturedAt(), cursor.upperId(), null);
    }

    private static void validate(PeriodSearchRequest request) {
        try {
            request.validate();
            if (request.projectId() == null) throw new IllegalArgumentException("Project id is required");
        } catch (IllegalArgumentException invalid) {
            throw new InvalidRequestException(invalid.getMessage());
        }
    }
}
