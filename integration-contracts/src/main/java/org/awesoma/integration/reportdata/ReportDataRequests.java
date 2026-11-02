package org.awesoma.integration.reportdata;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Requests report-service sends to the services that own monitoring data. Each validates
 * itself the same way on every side, and names the scope its cursor is bound to.
 */
public final class ReportDataRequests {
    /** A page never holds more rows than this. */
    public static final int MAX_PAGE_SIZE = 50;
    /** A sanity bound; report-service applies its own, smaller, configured limit. */
    public static final int MAX_MONITORS = 1000;

    private ReportDataRequests() {
    }

    /** {@code monitorIds == null} selects every monitor of the project. */
    public record MonitorSelectionRequest(List<Long> monitorIds, int limit, String cursor) {

        public void validate() {
            if (monitorIds != null) requireMonitors(monitorIds);
            requireLimit(limit);
        }

        public String scope(long projectId) {
            return "monitors|" + projectId + "|" + (monitorIds == null ? "all" : canonical(monitorIds));
        }
    }

    /** Asks only whether every listed monitor belongs to the project. */
    public record MonitorOwnershipRequest(List<Long> monitorIds) {

        public void validate() {
            requireMonitors(monitorIds);
        }
    }

    /**
     * Rows of the listed monitors over the half-open period {@code [from, to)}. For checks the
     * project is part of the request, since check-service keeps no projects of its own.
     */
    public record PeriodSearchRequest(Long projectId, List<Long> monitorIds, Instant from, Instant to, int limit,
            String cursor) {

        public void validate() {
            if (projectId != null && projectId <= 0) throw new IllegalArgumentException("Invalid project id");
            requireMonitors(monitorIds);
            if (from == null || to == null || !from.isBefore(to)) {
                throw new IllegalArgumentException("Period must have from before to");
            }
            requireLimit(limit);
        }

        public String scope(String source, long projectId) {
            return source + "|" + projectId + "|" + canonical(monitorIds) + "|" + from + "|" + to;
        }
    }

    private static void requireMonitors(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > MAX_MONITORS) {
            throw new IllegalArgumentException("Between 1 and " + MAX_MONITORS + " monitor IDs are required");
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Monitor IDs must be positive");
        }
    }

    private static void requireLimit(int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private static String canonical(List<Long> ids) {
        return new TreeSet<>(ids).stream().map(Objects::toString).collect(Collectors.joining(","));
    }
}
