package org.awesoma.report.application.usecase;

import org.awesoma.report.domain.ReportSource;
import org.awesoma.report.domain.ReportStatus;

public record ListReportsQuery(long projectId, long userId, ReportStatus status, ReportSource source, int page,
        int size) {
}
