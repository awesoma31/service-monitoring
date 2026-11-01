package org.awesoma.monitoring.integration.messaging;

import lombok.RequiredArgsConstructor;
import org.awesoma.integration.IntegrationEvent;
import org.awesoma.monitoring.domain.enums.CheckResultType;
import org.awesoma.monitoring.domain.model.ProbeOutcome;
import org.awesoma.monitoring.service.MonitorCheckService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MonitorEventHandler {
    private final JdbcTemplate db;
    private final MonitorCheckService checks;

    @Transactional
    public void handle(IntegrationEvent event) {
        if (event == null) throw new IllegalArgumentException("Missing event");
        event.validate();
        if (!IntegrationEvent.CHECK_COMPLETED.equals(event.type())) {
            throw new IllegalArgumentException("Unexpected event type");
        }
        int inserted = db.update("INSERT INTO integration_inbox (event_id) VALUES (?) ON CONFLICT DO NOTHING", event.eventId());
        if (inserted == 0) return;
        var result = event.outcome();
        checks.record(event.monitorId(), new ProbeOutcome(CheckResultType.valueOf(result.result()),
                result.responseMs(), result.httpStatus(), result.errorMessage(), result.claimToken()), event.occurredAt());
    }
}
