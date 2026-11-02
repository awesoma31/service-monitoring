package org.awesoma.report.application.port;

import java.util.Optional;
import java.util.UUID;
import org.awesoma.report.domain.Report;

public interface ReportRepository {

    Optional<Report> findById(UUID id);

    /**
     * Reads the report and keeps it locked until the current transaction ends, so that a check
     * of its state and the change that follows cannot interleave with another writer.
     */
    Optional<Report> lockById(UUID id);

    Report save(Report report);

    /** Visible reports of a project, newest first by creation time and then by ID. */
    ReportPage findVisible(ReportQuery query);
}
