package org.awesoma.report.domain.content;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.awesoma.report.domain.ReportPeriod;
import org.awesoma.report.domain.source.CheckRecord;
import org.awesoma.report.domain.source.IncidentRecord;
import org.awesoma.report.domain.source.MonitorInfo;
import org.awesoma.report.domain.source.ProjectInfo;

/**
 * Sums up checks and incidents page by page, keeping only counters per monitor, so a report
 * never needs all source rows in memory. A new attempt starts a new aggregation: a page read
 * twice in one attempt would be counted twice.
 *
 * <p>The success ratio is {@code 100 × SUCCESS / total} — a share of checks, not uptime. The
 * response times include every check with a measured time, {@code 0} too, whatever its
 * outcome. Ratio and average are rounded HALF_UP to two places only when the content is made.
 */
public final class ReportAggregation {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ProjectInfo project;
    private final ReportPeriod period;
    private final Map<Long, Tally> tallies = new LinkedHashMap<>();

    /** Every selected monitor gets a row, with or without checks in the period. */
    public ReportAggregation(ProjectInfo project, ReportPeriod period, List<MonitorInfo> monitors) {
        this.project = project;
        this.period = period;
        monitors.forEach(monitor -> tallies.put(monitor.id(), new Tally(monitor)));
    }

    /** Checks outside {@code [from, to)} or of a monitor not in the report are ignored. */
    public void add(CheckRecord check) {
        Tally tally = tallies.get(check.monitorId());
        if (tally == null || !period.contains(check.checkedAt())) return;
        tally.total++;
        switch (check.outcome()) {
            case SUCCESS -> tally.success++;
            case BAD_STATUS -> tally.badStatus++;
            case TIMEOUT -> tally.timeout++;
            case CONNECTION_ERROR -> tally.connectionError++;
        }
        if (check.responseMs() != null) {
            long ms = check.responseMs();
            tally.measured++;
            tally.sumMs += ms;
            tally.minMs = tally.minMs == null ? ms : Math.min(tally.minMs, ms);
            tally.maxMs = tally.maxMs == null ? ms : Math.max(tally.maxMs, ms);
        }
    }

    /** Incidents whose interval does not overlap the period are ignored. */
    public void add(IncidentRecord incident) {
        Tally tally = tallies.get(incident.monitorId());
        if (tally == null || !period.overlaps(incident.startedAt(), incident.resolvedAt())) return;
        tally.incidents++;
        switch (incident.severity()) {
            case LOW -> tally.low++;
            case MEDIUM -> tally.medium++;
            case HIGH -> tally.high++;
            case CRITICAL -> tally.critical++;
        }
        if (period.openAtEnd(incident.resolvedAt())) tally.openAtEnd++;
    }

    public int monitorCount() {
        return tallies.size();
    }

    public ReportContent content(Instant requestedAt, Instant generatedAt) {
        return new ReportContent(project, period, requestedAt, generatedAt,
                tallies.values().stream().map(Tally::row).toList());
    }

    private static final class Tally {
        private final MonitorInfo monitor;
        private long total;
        private long success;
        private long badStatus;
        private long timeout;
        private long connectionError;
        private long measured;
        private long sumMs;
        private Long minMs;
        private Long maxMs;
        private long incidents;
        private long low;
        private long medium;
        private long high;
        private long critical;
        private long openAtEnd;

        Tally(MonitorInfo monitor) {
            this.monitor = monitor;
        }

        MonitorRow row() {
            BigDecimal ratio = total == 0 ? null
                    : BigDecimal.valueOf(success).multiply(HUNDRED).divide(BigDecimal.valueOf(total), 2,
                            RoundingMode.HALF_UP);
            BigDecimal average = measured == 0 ? null
                    : BigDecimal.valueOf(sumMs).divide(BigDecimal.valueOf(measured), 2, RoundingMode.HALF_UP);
            return new MonitorRow(monitor,
                    new CheckStats(total, success, badStatus, timeout, connectionError, ratio, minMs, average, maxMs),
                    new IncidentStats(incidents, low, medium, high, critical, openAtEnd));
        }
    }
}
