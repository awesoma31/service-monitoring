package org.awesoma.report.domain.content;

import java.time.Instant;
import java.util.List;
import org.awesoma.report.domain.ReportPeriod;
import org.awesoma.report.domain.source.ProjectInfo;

/** The one model both CSV and PDF are rendered from, so the two never disagree. */
public record ReportContent(
        ProjectInfo project,
        ReportPeriod period,
        Instant requestedAt,
        Instant generatedAt,
        List<MonitorRow> rows) {

    public ReportContent {
        rows = List.copyOf(rows);
    }
}
