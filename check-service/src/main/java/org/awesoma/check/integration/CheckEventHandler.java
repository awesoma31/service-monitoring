package org.awesoma.check.integration;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.awesoma.check.repository.CheckResultRepository;
import org.awesoma.integration.IntegrationEvent;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CheckEventHandler {
    private final DatabaseClient db;
    private final CheckResultRepository results;
    private final TransactionalOperator transaction;

    public Mono<Void> handle(IntegrationEvent event) {
        return Mono.defer(() -> {
            if (event == null) throw new IllegalArgumentException("Missing event");
            event.validate();
            List<Long> monitors = switch (event.type()) {
                case IntegrationEvent.MONITOR_DELETED -> List.of(event.monitorId());
                case IntegrationEvent.PROJECT_DELETED -> event.monitorIds();
                default -> throw new IllegalArgumentException("Unexpected event type");
            };
            return transaction.transactional(db.sql("INSERT INTO integration_inbox (event_id) VALUES (:id) ON CONFLICT DO NOTHING")
                    .bind("id", event.eventId()).fetch().rowsUpdated()
                    .flatMap(count -> count == 0 ? Mono.empty() : Flux.fromIterable(monitors.stream().distinct().sorted().toList())
                            .concatMap(this::deleteMonitor).then()));
        });
    }

    public Mono<Void> deleteMonitorLegacy(Long monitorId) {
        return transaction.transactional(deleteMonitor(monitorId));
    }

    private Mono<Void> deleteMonitor(Long id) {
        return db.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(:id)) AS acquired")
                .bind("id", id).fetch().all().then()
                .then(db.sql("INSERT INTO deleted_monitors (monitor_id) VALUES (:id) ON CONFLICT DO NOTHING")
                        .bind("id", id).fetch().rowsUpdated())
                .then(results.deleteByMonitorId(id)).then();
    }
}
