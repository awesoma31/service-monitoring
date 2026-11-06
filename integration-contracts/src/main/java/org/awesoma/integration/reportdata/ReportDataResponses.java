package org.awesoma.integration.reportdata;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/** Pages the data-owning services return to report-service. */
public final class ReportDataResponses {
    /**
     * The {@code code} of a refusal in the problem body. report-service acts only on these: a
     * bare 404, such as one from a missing route, is an outage and never deletes a report.
     */
    public static final String PROJECT_NOT_FOUND = "PROJECT_NOT_FOUND";
    public static final String MONITOR_NOT_IN_PROJECT = "MONITOR_NOT_IN_PROJECT";

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

        /** Builds a page from {@code limit + 1} rows: the extra row only tells that more follow. */
        public static <R, T> Page<T> of(List<R> rows, int limit, ReportDataCursor cursor, String scope,
                ToLongFunction<R> id, Function<R, T> item, ProjectItem project) {
            boolean hasNext = rows.size() > limit;
            var visible = hasNext ? rows.subList(0, limit) : rows;
            String next = hasNext ? cursor.after(id.applyAsLong(visible.get(limit - 1))).encode(scope) : null;
            return new Page<>(visible.stream().map(item).toList(), next, hasNext, cursor.capturedAt(),
                    cursor.upperId(), project);
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
