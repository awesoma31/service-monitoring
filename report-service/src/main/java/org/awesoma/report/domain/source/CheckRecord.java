package org.awesoma.report.domain.source;

import java.time.Instant;

/** One check; {@code responseMs} is {@code null} when no response time was measured. */
public record CheckRecord(long id, long monitorId, CheckOutcome outcome, Long responseMs, Instant checkedAt) {
}
