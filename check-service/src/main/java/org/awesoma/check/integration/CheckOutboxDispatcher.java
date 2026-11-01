package org.awesoma.check.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.integration.IntegrationEvent;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "integration.outbox.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
class CheckOutboxDispatcher {
    private final DatabaseClient db;
    private final ObjectMapper json;
    private final TransactionalOperator transaction;
    private final CheckResultPublisher publisher;

    @Scheduled(fixedDelayString = "${integration.outbox.interval-ms:1000}")
    public void dispatch() {
        try {
            Flux.range(0, 100)
                    .concatMap(ignored -> transaction.transactional(
                            db.sql("SELECT pg_try_advisory_xact_lock(hashtext('check-outbox')) AS locked")
                                    .map((row, meta) -> Boolean.TRUE.equals(row.get("locked", Boolean.class))).one()
                                    .flatMap(locked -> locked ? processOldest() : Mono.just(false))))
                    .takeUntil(processed -> !processed).then().block();
        } catch (RuntimeException failure) {
            log.warn("Check outbox delivery failed", failure);
        }
    }

    private Mono<Boolean> processOldest() {
        return db.sql("SELECT id, transport, payload FROM integration_outbox ORDER BY id LIMIT 1 FOR UPDATE")
                .map((row, meta) -> new OutboxEntry(row.get("id", Long.class),
                        row.get("transport", String.class), row.get("payload", String.class))).one()
                .flatMap(row -> {
                    if (!publisher.transport().equals(row.transport())) return Mono.just(false);
                    try {
                        IntegrationEvent event = json.readValue(row.payload(), IntegrationEvent.class);
                        return publisher.publish(event).then(db.sql("DELETE FROM integration_outbox WHERE id = :id")
                                .bind("id", row.id()).fetch().rowsUpdated()).thenReturn(true);
                    } catch (JsonProcessingException e) { return Mono.error(e); }
                }).defaultIfEmpty(false);
    }

    private record OutboxEntry(Long id, String transport, String payload) {}
}
