package org.awesoma.report.application.usecase;

import java.time.Clock;
import java.util.UUID;
import org.awesoma.report.application.port.EventInbox;
import org.awesoma.report.application.port.ProjectGuard;
import org.awesoma.report.application.port.ReportRepository;
import org.awesoma.report.application.port.TransactionRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reacts to a deleted project, whether the event came over Kafka or HTTP. In one transaction
 * it records the event, marks the project as deleted and moves every report and job of the
 * project to DELETING, revoking their leases; the project's lock keeps an upload or a
 * generation from being created or published in between. The objects are removed afterwards
 * by the background cleanup. A repeated event finds its ID in the inbox and changes nothing.
 */
public class ProjectDeletionService {
    private static final Logger log = LoggerFactory.getLogger(ProjectDeletionService.class);
    private final ReportRepository reports;
    private final ProjectGuard projects;
    private final EventInbox inbox;
    private final TransactionRunner transactions;
    private final Clock clock;

    public ProjectDeletionService(ReportRepository reports, ProjectGuard projects, EventInbox inbox,
            TransactionRunner transactions, Clock clock) {
        this.reports = reports;
        this.projects = projects;
        this.inbox = inbox;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** @return how many reports were moved to DELETING; 0 for a repeated event */
    public int projectDeleted(UUID eventId, long projectId) {
        int marked = transactions.inTransaction(() -> {
            if (!inbox.record(eventId)) return 0;
            projects.lock(projectId);
            projects.markDeleted(projectId);
            int deleting = 0;
            for (var report : reports.lockVisibleOfProject(projectId)) {
                if (report.markDeleting(clock.instant())) {
                    reports.save(report);
                    deleting++;
                }
            }
            return deleting;
        });
        log.info("Project deletion event handled: event_id={} project_id={} reports_marked={}", eventId, projectId, marked);
        return marked;
    }

    /** A deleted monitor needs nothing here: reports of its project stay as they are. */
    public void monitorDeleted(UUID eventId) {
        transactions.inTransaction(() -> inbox.record(eventId));
    }
}
