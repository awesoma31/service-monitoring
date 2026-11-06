package org.awesoma.check.adapters.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.awesoma.check.application.port.CheckResultRecorder;
import org.awesoma.check.domain.CheckResult;
import org.awesoma.check.domain.ProbeOutcome;
import org.awesoma.check.adapters.out.persistence.repository.CheckResultRepository;
import org.awesoma.integration.IntegrationEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
class R2dbcCheckResultRecorder implements CheckResultRecorder {
    private final DatabaseClient db;
    private final CheckResultRepository results;
    private final ObjectMapper json;
    private final TransactionalOperator transaction;
    @Value("${integration.check-results.transport:http}") private String transport;

    @Override
    public Mono<Void> record(Long monitorId, ProbeOutcome outcome, OffsetDateTime checkedAt) {
        return Mono.defer(() -> {
            var payload = new IntegrationEvent.CheckOutcome(outcome.result().name(), outcome.responseMs(),
                    outcome.httpStatus(), outcome.errorMessage(), outcome.claimToken());
            var event = new IntegrationEvent(UUID.randomUUID(), 1, IntegrationEvent.CHECK_COMPLETED,
                    checkedAt, monitorId, null, null, null, null, payload);
            event.validate();
            String serialized;
            try { serialized = json.writeValueAsString(event); }
            catch (JsonProcessingException e) { return Mono.error(e); }
            return transaction.transactional(lock(monitorId)
                    .then(db.sql("SELECT monitor_id FROM deleted_monitors WHERE monitor_id = :id")
                            .bind("id", monitorId).fetch().first().hasElement())
                    .flatMap(deleted -> deleted ? Mono.empty() : results.save(CheckResult.of(monitorId, outcome, checkedAt))
                            .then(db.sql("INSERT INTO integration_outbox (event_id, transport, payload) VALUES (:id, :transport, :payload)")
                                    .bind("id", event.eventId()).bind("transport", transport).bind("payload", serialized)
                                    .fetch().rowsUpdated()).then()));
        });
    }

    Mono<Void> lock(Long monitorId) {
        return db.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(:id)) AS acquired")
                .bind("id", monitorId).fetch().all().then();
    }
}
