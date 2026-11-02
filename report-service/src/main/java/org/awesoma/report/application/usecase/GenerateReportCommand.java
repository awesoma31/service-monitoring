package org.awesoma.report.application.usecase;

import java.time.OffsetDateTime;
import java.util.List;
import org.awesoma.report.domain.ReportFormat;

/** {@code monitorIds} and {@code idempotencyKey} are optional; no IDs means every monitor. */
public record GenerateReportCommand(
        long projectId,
        long userId,
        OffsetDateTime from,
        OffsetDateTime to,
        ReportFormat format,
        List<Long> monitorIds,
        String idempotencyKey) {
}
