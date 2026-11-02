package org.awesoma.integration.reportdata;

import java.time.Instant;
import java.util.List;

/** Pages the data-owning services return to report-service. */
public final class ReportDataResponses {

    private ReportDataResponses() {
    }

    /**
     * {@code nextCursor} is {@code null} on the last page. {@code upperId} and
     * {@code sourceCapturedAt} are the bounds fixed when the walk started; {@code project}
     * is filled only by the monitor selection.
     */
    public record Page<T>(List<T> items, String nextCursor, boolean hasNext, Instant sourceCapturedAt,
            Long upperId, ProjectItem project) {

        public Page {
            items = List.copyOf(items);
        }
    }

    public record ProjectItem(long id, String name) {
    }

    public record MonitorItem(long id, String name, String url) {
    }

    public record IncidentItem(long id, long monitorId, String severity, Instant startedAt, Instant resolvedAt) {
    }

    public record CheckItem(long id, long monitorId, String result, Integer responseMs, Instant checkedAt) {
    }

    public enum Ownership {
        OWNED,
        PROJECT_NOT_FOUND,
        MONITOR_NOT_IN_PROJECT
    }

    /** Answered with 200 in every case, so that a refusal never counts as a service failure. */
    public record OwnershipResponse(Ownership ownership) {
    }
}
