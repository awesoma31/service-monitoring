package org.awesoma.report.application.port;

import java.util.List;
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

    /** Removes the row once nothing of the report is left in storage. */
    void delete(UUID id);

    /** Visible reports of a project, newest first by creation time and then by ID. */
    ReportPage findVisible(ReportQuery query);

    /** Locks every report of the project that is not being deleted yet. */
    List<Report> lockVisibleOfProject(long projectId);

    /** Reports waiting for their objects to be removed, oldest first. */
    List<UUID> findDeleting(int limit);
}
