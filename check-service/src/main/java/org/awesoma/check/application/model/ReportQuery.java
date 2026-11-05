package org.awesoma.check.application.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

public record ReportQuery(Long projectId, List<Long> monitorIds, Instant from, Instant to, int limit, String cursor) {
    public void validate() {
        if (projectId == null) throw new IllegalArgumentException("Project id is required");
        if (projectId <= 0) throw new IllegalArgumentException("Invalid project id");
        if (monitorIds == null || monitorIds.isEmpty() || monitorIds.size() > 1000)
            throw new IllegalArgumentException("Between 1 and 1000 monitor IDs are required");
        if (monitorIds.stream().anyMatch(id -> id == null || id <= 0))
            throw new IllegalArgumentException("Monitor IDs must be positive");
        if (from == null || to == null || !from.isBefore(to))
            throw new IllegalArgumentException("Period must have from before to");
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("Limit must be between 1 and 50");
    }
    public String scope() {
        return "checks|" + projectId + "|" + new TreeSet<>(monitorIds).stream()
                .map(Objects::toString).collect(Collectors.joining(",")) + "|" + from + "|" + to;
    }
}
