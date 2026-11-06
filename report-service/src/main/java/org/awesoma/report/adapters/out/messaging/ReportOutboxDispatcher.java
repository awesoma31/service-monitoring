package org.awesoma.report.adapters.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.integration.IntegrationEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends the oldest event and removes it in one transaction. A failure keeps the row for the next
 * run with the same event ID, so delivery is at least once and the receiver skips a repeat.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "report.jobs.enabled", havingValue = "true", matchIfMissing = true)
class ReportOutboxDispatcher {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final ReportEventPublisher publisher;

    @Scheduled(fixedDelayString = "${integration.outbox.interval-ms:1000}")
    void dispatch() {
        try {
            while (Boolean.TRUE.equals(transaction.execute(status -> sendOldest()))) {
                // Keep going while there are events to send.
            }
        } catch (RuntimeException failure) {
            log.warn("Report outbox delivery failed", failure);
        }
    }

    private boolean sendOldest() {
        Boolean locked = db.queryForObject("SELECT pg_try_advisory_xact_lock(hashtext('report-outbox'))", Boolean.class);
        if (!Boolean.TRUE.equals(locked)) return false;
        return Boolean.TRUE.equals(db.query(
                "SELECT id, transport, payload FROM integration_outbox ORDER BY id LIMIT 1 FOR UPDATE", rs -> {
                    if (!rs.next()) return false;
                    long id = rs.getLong("id");
                    // Written under another transport: stops here until that setting is restored.
                    if (!publisher.transport().equals(rs.getString("transport"))) return false;
                    publisher.publish(decode(rs.getString("payload")));
                    db.update("DELETE FROM integration_outbox WHERE id = ?", id);
                    return true;
                }));
    }

    private IntegrationEvent decode(String value) {
        try {
            return json.readValue(value, IntegrationEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid persisted event", e);
        }
    }
}
