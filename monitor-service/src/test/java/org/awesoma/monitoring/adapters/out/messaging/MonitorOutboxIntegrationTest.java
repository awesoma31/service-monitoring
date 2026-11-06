package org.awesoma.monitoring.adapters.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.awesoma.monitoring.application.event.IncidentChanged;
import org.awesoma.monitoring.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = "integration.outbox.enabled=false")
class MonitorOutboxIntegrationTest extends AbstractIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired ApplicationEventPublisher events;
    @Autowired TransactionTemplate tx;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired IncidentEventPublisher incidents;
    @Autowired ResourceEventPublisher resources;

    @Test
    void commitPersistsEventRollbackDiscardsItAndDeliveryRetries() {
        db.update("DELETE FROM integration_outbox");
        tx.executeWithoutResult(status -> {
            events.publishEvent(new IncidentChanged(5551L, 5552L, IncidentChanged.Kind.OPENED));
            status.setRollbackOnly();
        });
        assertThat(db.queryForObject("SELECT count(*) FROM integration_outbox", Integer.class)).isZero();
        tx.executeWithoutResult(status -> events.publishEvent(
                new IncidentChanged(5551L, 5552L, IncidentChanged.Kind.OPENED)));
        tx.executeWithoutResult(status -> events.publishEvent(
                new IncidentChanged(5551L, 5552L, IncidentChanged.Kind.RESOLVED)));
        assertThat(db.queryForObject("SELECT count(*) FROM integration_outbox", Integer.class)).isEqualTo(2);
        doThrow(new IllegalStateException("offline")).doNothing().when(notificationClient).integrationEvent(any());
        var dispatcher = new MonitorOutboxDispatcher(db, json, tx, incidents, resources);
        dispatcher.dispatch();
        assertThat(db.queryForObject("SELECT count(*) FROM integration_outbox", Integer.class)).isEqualTo(2);
        dispatcher.dispatch();
        assertThat(db.queryForObject("SELECT count(*) FROM integration_outbox", Integer.class)).isEqualTo(1);
        dispatcher.dispatch();
        assertThat(db.queryForObject("SELECT count(*) FROM integration_outbox", Integer.class)).isZero();
        var captured = org.mockito.ArgumentCaptor.forClass(org.awesoma.integration.IntegrationEvent.class);
        verify(notificationClient, times(3)).integrationEvent(captured.capture());
        assertThat(captured.getAllValues()).extracting(org.awesoma.integration.IntegrationEvent::kind)
                .containsExactly("OPENED", "OPENED", "RESOLVED");
    }
}
