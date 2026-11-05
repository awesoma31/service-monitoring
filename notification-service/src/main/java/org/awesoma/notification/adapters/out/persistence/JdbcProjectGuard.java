package org.awesoma.notification.adapters.out.persistence;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.awesoma.notification.application.port.ProjectGuard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JdbcProjectGuard implements ProjectGuard {
    private final JdbcTemplate db;
    public void lock(Long projectId) {
        db.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock(?)) AS acquired", Integer.class, projectId);
    }
    public boolean deleted(Long projectId) {
        Integer count = db.queryForObject("SELECT count(*) FROM deleted_projects WHERE project_id = ?", Integer.class, projectId);
        return count != null && count > 0;
    }
    public void markDeleted(Long projectId) {
        db.update("INSERT INTO deleted_projects (project_id) VALUES (?) ON CONFLICT DO NOTHING", projectId);
    }
    public boolean recordEvent(UUID eventId) {
        return db.update("INSERT INTO integration_inbox (event_id) VALUES (?) ON CONFLICT DO NOTHING", eventId) == 1;
    }
}
