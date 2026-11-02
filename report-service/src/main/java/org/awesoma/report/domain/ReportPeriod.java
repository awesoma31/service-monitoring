package org.awesoma.report.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

/** A half-open UTC interval {@code [from, to)}. */
public record ReportPeriod(Instant from, Instant to) {

    public ReportPeriod {
        if (from == null || to == null) {
            throw new InvalidReportRequestException(from == null ? "from" : "to", "Period bounds are required");
        }
        if (!from.isBefore(to)) {
            throw new InvalidReportRequestException("from", "Period start must be before its end");
        }
    }

    /**
     * Bounds arrive with their own offsets and are normalised to UTC. The period may not end
     * after the request was made, so a report never promises data that does not exist yet.
     */
    public static ReportPeriod requested(OffsetDateTime from, OffsetDateTime to, Instant requestedAt,
            Duration maxLength) {
        if (from == null || to == null) {
            throw new InvalidReportRequestException(from == null ? "from" : "to",
                    "Period bounds with a time zone are required");
        }
        var period = new ReportPeriod(from.toInstant(), to.toInstant());
        if (period.to.isAfter(requestedAt)) {
            throw new InvalidReportRequestException("to", "Period cannot end in the future");
        }
        if (period.length().compareTo(maxLength) > 0) {
            throw new InvalidReportRequestException("to", "Period is longer than " + maxLength.toDays() + " days");
        }
        return period;
    }

    public Duration length() {
        return Duration.between(from, to);
    }

    public boolean contains(Instant moment) {
        return !moment.isBefore(from) && moment.isBefore(to);
    }

    /** An interval overlaps the period when it starts before its end and is not over by its start. */
    public boolean overlaps(Instant startedAt, Instant resolvedAt) {
        return startedAt.isBefore(to) && (resolvedAt == null || resolvedAt.isAfter(from));
    }

    /** Still open at the end of the period; one closed exactly at {@code to} counts as closed. */
    public boolean openAtEnd(Instant resolvedAt) {
        return resolvedAt == null || resolvedAt.isAfter(to);
    }
}
