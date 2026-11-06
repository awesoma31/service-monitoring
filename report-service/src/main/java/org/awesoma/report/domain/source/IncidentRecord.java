package org.awesoma.report.domain.source;

import java.time.Instant;

/** One incident; {@code resolvedAt} is {@code null} while it is open. */
public record IncidentRecord(long id, long monitorId, IncidentSeverity severity, Instant startedAt,
        Instant resolvedAt) {
}
