package org.awesoma.report.application.port;

import org.awesoma.report.domain.ReportSource;
import org.awesoma.report.domain.ReportStatus;

/** {@code status} and {@code source} are optional filters. */
public record ReportQuery(long projectId, ReportStatus status, ReportSource source, int page, int size) {
}
