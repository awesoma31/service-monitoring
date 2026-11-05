package org.awesoma.check.adapters.out.persistence;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.awesoma.check.application.port.DeletionStore;
import org.awesoma.check.repository.CheckResultRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class R2dbcDeletionStore implements DeletionStore {
    private final DatabaseClient db;
    private final CheckResultRepository results;

    public Mono<Boolean> recordEvent(UUID id) {
        return db.sql("INSERT INTO integration_inbox (event_id) VALUES (:id) ON CONFLICT DO NOTHING")
                .bind("id", id).fetch().rowsUpdated().map(count -> count > 0);
    }

    public Mono<Void> deleteMonitor(Long id) {
        return db.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(:id)) AS acquired")
                .bind("id", id).fetch().all().then()
                .then(db.sql("INSERT INTO deleted_monitors (monitor_id) VALUES (:id) ON CONFLICT DO NOTHING")
                        .bind("id", id).fetch().rowsUpdated())
                .then(results.deleteByMonitorId(id)).then();
    }
}
