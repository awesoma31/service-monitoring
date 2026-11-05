package org.awesoma.monitoring.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.function.LongSupplier;
import lombok.RequiredArgsConstructor;
import org.awesoma.integration.reportdata.ReportDataCursor;
import org.awesoma.integration.reportdata.ReportDataRequests.MonitorOwnershipRequest;
import org.awesoma.integration.reportdata.ReportDataRequests.MonitorSelectionRequest;
import org.awesoma.integration.reportdata.ReportDataRequests.PeriodSearchRequest;
import org.awesoma.integration.reportdata.ReportDataResponses.IncidentItem;
import org.awesoma.integration.reportdata.ReportDataResponses.MonitorItem;
import org.awesoma.integration.reportdata.ReportDataResponses.Ownership;
import org.awesoma.integration.reportdata.ReportDataResponses.Page;
import org.awesoma.integration.reportdata.ReportDataResponses.ProjectItem;
import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.application.port.IncidentRepositoryPort;
import org.awesoma.monitoring.application.port.MonitorRepositoryPort;
import org.awesoma.monitoring.application.port.ProjectRepositoryPort;
import org.awesoma.monitoring.application.exception.InvalidRequestException;
import org.awesoma.monitoring.application.exception.MonitorNotInProjectException;
import org.awesoma.monitoring.application.exception.ProjectNotFoundException;


/**
 * Monitors and incidents for report generation, read page by page. A walk fixes the highest
 * ID when it starts and never goes past it; the pages are ordered by ID, so rows changing
 * between pages neither repeat nor shift others. The bounds are not an atomic snapshot: an
 * incident resolved during the walk is read as it is when its page is read.
 */
@RequiredArgsConstructor
public class ReportDataService {
    private final ProjectRepositoryPort projects;
    private final MonitorRepositoryPort monitors;
    private final IncidentRepositoryPort incidents;
    private final Clock clock = Clock.systemUTC();

    public Page<MonitorItem> monitors(long projectId, MonitorSelectionRequest request) {
        validate(request::validate);
        var project = projects.findById(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId));
        boolean all = request.monitorIds() == null;
        if (!all) requireOwned(projectId, request.monitorIds());
        var cursor = position(request.cursor(), request.scope(projectId), monitors::maxId);
        List<Long> ids = all ? List.of(-1L) : request.monitorIds();
        var rows = monitors.findReportPage(projectId, all, ids, cursor.lastId(), cursor.upperId(),
                request.limit() + 1);
        return Page.of(rows, request.limit(), cursor, request.scope(projectId), Monitor::getId,
                monitor -> new MonitorItem(monitor.getId(), monitor.getName(), monitor.getUrl()),
                new ProjectItem(project.getId(), project.getName()));
    }

    public Ownership ownership(long projectId, MonitorOwnershipRequest request) {
        validate(request::validate);
        if (!projects.existsById(projectId)) return Ownership.PROJECT_NOT_FOUND;
        long distinct = new HashSet<>(request.monitorIds()).size();
        return monitors.countByProjectIdAndIdIn(projectId, request.monitorIds()) == distinct
                ? Ownership.OWNED : Ownership.MONITOR_NOT_IN_PROJECT;
    }

    public Page<IncidentItem> incidents(long projectId, PeriodSearchRequest request) {
        validate(request::validate);
        if (!projects.existsById(projectId)) throw new ProjectNotFoundException(projectId);
        requireOwned(projectId, request.monitorIds());
        String scope = request.scope("incidents", projectId);
        var cursor = position(request.cursor(), scope, incidents::maxId);
        var rows = incidents.findReportPage(request.monitorIds(), request.from().atOffset(ZoneOffset.UTC),
                request.to().atOffset(ZoneOffset.UTC), cursor.lastId(), cursor.upperId(),
                request.limit() + 1);
        return Page.of(rows, request.limit(), cursor, scope, Incident::getId, incident -> new IncidentItem(
                incident.getId(), incident.getMonitor().getId(), incident.getSeverity().name(),
                incident.getStartedAt().toInstant(),
                incident.getResolvedAt() == null ? null : incident.getResolvedAt().toInstant()), null);
    }

    private void requireOwned(long projectId, List<Long> ids) {
        if (monitors.countByProjectIdAndIdIn(projectId, ids) != new HashSet<>(ids).size()) {
            throw new MonitorNotInProjectException(projectId);
        }
    }

    /** The first page fixes the upper bound; later pages carry it in their cursor. */
    private ReportDataCursor position(String token, String scope, LongSupplier maxId) {
        if (token != null) {
            try {
                return ReportDataCursor.decode(token, scope);
            } catch (IllegalArgumentException invalid) {
                throw new InvalidRequestException(invalid.getMessage());
            }
        }
        return ReportDataCursor.start(maxId.getAsLong(), Instant.now(clock));
    }

    private static void validate(Runnable validation) {
        try {
            validation.run();
        } catch (IllegalArgumentException invalid) {
            throw new InvalidRequestException(invalid.getMessage());
        }
    }
}
