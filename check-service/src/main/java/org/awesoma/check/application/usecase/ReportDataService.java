package org.awesoma.check.application.usecase;

import java.time.Clock;
import java.time.Instant;
import org.awesoma.check.application.exception.InvalidRequestException;
import org.awesoma.check.application.exception.MonitorNotInProjectException;
import org.awesoma.check.application.exception.ProjectNotFoundException;
import org.awesoma.check.application.model.ReportPage;
import org.awesoma.check.application.model.ReportPosition;
import org.awesoma.check.application.model.ReportQuery;
import org.awesoma.check.application.port.CursorCodec;
import org.awesoma.check.application.port.MonitorOwnership;
import org.awesoma.check.application.port.ReportDataStore;
import reactor.core.publisher.Mono;

public class ReportDataService {
    private final ReportDataStore data;
    private final MonitorOwnership monitors;
    private final CursorCodec cursors;
    private final Clock clock;
    public ReportDataService(ReportDataStore data, MonitorOwnership monitors, CursorCodec cursors, Clock clock) {
        this.data = data; this.monitors = monitors; this.cursors = cursors; this.clock = clock;
    }
    public Mono<ReportPage> checks(ReportQuery request) {
        return Mono.fromRunnable(() -> validate(request))
                .then(Mono.defer(() -> monitors.ownership(request.projectId(), request.monitorIds())))
                .flatMap(answer -> switch (answer) {
                    case OWNED -> position(request);
                    case PROJECT_NOT_FOUND -> Mono.error(new ProjectNotFoundException(request.projectId()));
                    case MONITOR_NOT_IN_PROJECT -> Mono.error(new MonitorNotInProjectException(request.projectId()));
                })
                .flatMap(cursor -> data.find(request, cursor).collectList().map(rows -> {
                    boolean hasNext = rows.size() > request.limit();
                    var visible = hasNext ? rows.subList(0, request.limit()) : rows;
                    String next = hasNext ? cursors.next(cursor, visible.get(request.limit() - 1).id(), request.scope()) : null;
                    return new ReportPage(visible, next, hasNext, cursor.capturedAt(), cursor.upperId());
                }));
    }
    private Mono<ReportPosition> position(ReportQuery request) {
        if (request.cursor() != null) {
            try { return Mono.just(cursors.decode(request.cursor(), request.scope())); }
            catch (IllegalArgumentException invalid) { return Mono.error(new InvalidRequestException(invalid.getMessage())); }
        }
        return data.upperId().map(upper -> cursors.start(upper, Instant.now(clock)));
    }
    private static void validate(ReportQuery request) {
        try { request.validate(); }
        catch (IllegalArgumentException invalid) { throw new InvalidRequestException(invalid.getMessage()); }
    }
}
