package org.awesoma.report.adapters.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.awesoma.integration.IntegrationEvent;
import org.awesoma.report.application.port.ReportEvents;
import org.awesoma.report.domain.Report;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records the event in the transaction that makes the report READY: both happen, or neither. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class OutboxReportEvents implements ReportEvents {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final Clock clock;
    private final String transport;

    OutboxReportEvents(JdbcTemplate db, ObjectMapper json, Clock clock,
            @Value("${integration.report-events.transport:http}") String transport) {
        this.db = db;
        this.json = json;
        this.clock = clock;
        this.transport = transport;
    }

    @Override
    public void uploaded(Report report) {
        var event = IntegrationEvent.reportUploaded(UUID.randomUUID(), clock.instant().atOffset(ZoneOffset.UTC),
                report.projectId(), report.id(), report.fileName().value(), report.createdBy());
        event.validate();
        try {
            db.update("INSERT INTO integration_outbox (event_id, transport, payload) VALUES (?, ?, ?)",
                    event.eventId(), transport, json.writeValueAsString(event));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot serialize integration event", failure);
        }
    }
}
