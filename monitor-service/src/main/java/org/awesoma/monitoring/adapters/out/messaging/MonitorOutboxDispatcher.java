package org.awesoma.monitoring.adapters.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.integration.IntegrationEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Dispatch each flow in commit order; a failed send leaves its row for a later retry. */
@Component
@ConditionalOnProperty(name = "integration.outbox.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class MonitorOutboxDispatcher {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final IncidentEventPublisher incidents;
    private final ResourceEventPublisher resources;

    @Scheduled(fixedDelayString = "${integration.outbox.interval-ms:1000}")
    public void dispatch() {
        for (String flow : List.of("incident", "resource")) {
            try {
                transaction.executeWithoutResult(status -> dispatchFlow(flow));
            } catch (RuntimeException failure) {
                log.warn("Integration outbox delivery failed: flow={}", flow, failure);
            }
        }
    }

    private void dispatchFlow(String flow) {
        Boolean locked = db.queryForObject("SELECT pg_try_advisory_xact_lock(hashtext(?))", Boolean.class,
                "monitor-outbox-" + flow);
        if (!Boolean.TRUE.equals(locked)) return;
        db.query("SELECT id, transport, payload FROM integration_outbox WHERE flow = ? ORDER BY id LIMIT 1 FOR UPDATE",
                rs -> {
                    long id = rs.getLong("id");
                    String transport = rs.getString("transport");
                    IntegrationEvent event = decode(rs.getString("payload"));
                    if ("incident".equals(flow)) {
                        if (!incidents.transport().equals(transport)) return;
                        incidents.publish(event);
                    } else {
                        if (!resources.transport().equals(transport)) return;
                        resources.publish(event);
                    }
                    db.update("DELETE FROM integration_outbox WHERE id = ?", id);
                }, flow);
    }

    private IntegrationEvent decode(String value) {
        try {
            return json.readValue(value, IntegrationEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid persisted event", e);
        }
    }
}
