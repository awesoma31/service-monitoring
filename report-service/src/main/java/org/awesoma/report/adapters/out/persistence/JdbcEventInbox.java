package org.awesoma.report.adapters.out.persistence;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.awesoma.report.application.port.EventInbox;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
class JdbcEventInbox implements EventInbox {
    private final JdbcTemplate db;

    @Override
    public boolean record(UUID eventId) {
        return db.update("INSERT INTO integration_inbox (event_id) VALUES (?) ON CONFLICT DO NOTHING", eventId) == 1;
    }
}
