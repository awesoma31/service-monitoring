package org.awesoma.report.adapters.out.persistence;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.awesoma.report.application.port.EventInbox;
import org.awesoma.report.application.port.ProjectGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The project lock is a transaction-scoped advisory lock keyed by the project ID, so it needs
 * no row and is released by the commit. The deletion marker and the inbox are plain rows.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
class JdbcProjectGuard implements ProjectGuard, EventInbox {
    private final JdbcTemplate db;

    /** Advisory lock keys belong to the database, and report_db takes no other advisory locks. */
    @Override
    public void lock(long projectId) {
        db.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock(?)) AS held", Integer.class, projectId);
    }

    @Override
    public boolean deleted(long projectId) {
        return Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM deleted_projects WHERE project_id = ?)", Boolean.class, projectId));
    }

    @Override
    public void markDeleted(long projectId) {
        db.update("INSERT INTO deleted_projects (project_id) VALUES (?) ON CONFLICT DO NOTHING", projectId);
    }

    @Override
    public boolean record(UUID eventId) {
        return db.update("INSERT INTO integration_inbox (event_id) VALUES (?) ON CONFLICT DO NOTHING", eventId) == 1;
    }
}
