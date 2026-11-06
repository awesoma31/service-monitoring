package org.awesoma.check.adapters.out.messaging;

import org.awesoma.check.adapters.in.messaging.CheckEventHandler;
import org.awesoma.check.application.port.CheckResultRecorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.awesoma.check.domain.ProbeOutcome;
import org.awesoma.check.support.AbstractIntegrationTest;
import org.awesoma.integration.IntegrationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.reactive.TransactionalOperator;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@TestPropertySource(properties = "integration.outbox.enabled=false")
class CheckOutboxIntegrationTest extends AbstractIntegrationTest {
    @Autowired CheckResultRecorder recorder;
    @Autowired CheckEventHandler deletions;
    @Autowired DatabaseClient db;
    @Autowired ObjectMapper json;
    @Autowired TransactionalOperator transaction;

    @BeforeEach
    void clearOutbox() {
        db.sql("DELETE FROM integration_outbox").fetch().rowsUpdated().block();
    }

    @Test
    void historyAndOutboxCommitTogetherAndDeletionFencesLaterResults() {
        long id = 990001L;
        var outcome = ProbeOutcome.success(15, 200).forClaim(UUID.randomUUID());
        recorder.record(id, outcome, OffsetDateTime.now()).block();
        assertThat(count("SELECT count(*) FROM check_results WHERE monitor_id = :id", id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM integration_outbox WHERE payload LIKE :id", "%990001%")).isEqualTo(1);
        var deletion = new IntegrationEvent(UUID.randomUUID(), 1, IntegrationEvent.MONITOR_DELETED,
                OffsetDateTime.now(), id, null, null, null, null, null);
        deletions.handle(deletion).block();
        deletions.handle(deletion).block();
        recorder.record(id, outcome, OffsetDateTime.now()).block();
        assertThat(count("SELECT count(*) FROM check_results WHERE monitor_id = :id", id)).isZero();
        assertThat(count("SELECT count(*) FROM integration_outbox WHERE payload LIKE :id", "%990001%")).isEqualTo(1);
    }

    @Test
    void outboxFailureRollsBackTheCheckHistory() {
        long id = 990002L;
        db.sql("ALTER TABLE integration_outbox ADD CONSTRAINT test_reject_outbox CHECK (false) NOT VALID")
                .fetch().rowsUpdated().block();
        try {
            assertThrows(RuntimeException.class, () -> recorder.record(id,
                    ProbeOutcome.success(10, 200).forClaim(UUID.randomUUID()), OffsetDateTime.now()).block());
            assertThat(count("SELECT count(*) FROM check_results WHERE monitor_id = :id", id)).isZero();
        } finally {
            db.sql("ALTER TABLE integration_outbox DROP CONSTRAINT test_reject_outbox").fetch().rowsUpdated().block();
        }
    }

    @Test
    void dispatcherKeepsFailedEventsAndRetriesThem() {
        recorder.record(990003L, ProbeOutcome.success(2, 200).forClaim(UUID.randomUUID()), OffsetDateTime.now()).block();
        CheckResultPublisher publisher = mock(CheckResultPublisher.class);
        when(publisher.transport()).thenReturn("http");
        when(publisher.publish(any())).thenReturn(Mono.error(new IllegalStateException("offline")), Mono.empty());
        var dispatcher = new CheckOutboxDispatcher(db, json, transaction, publisher);
        dispatcher.dispatch();
        assertThat(count("SELECT count(*) FROM integration_outbox WHERE payload LIKE :id", "%990003%")).isEqualTo(1);
        dispatcher.dispatch();
        assertThat(count("SELECT count(*) FROM integration_outbox WHERE payload LIKE :id", "%990003%")).isZero();
        verify(publisher, times(2)).publish(any());
    }

    private long count(String query, Object value) {
        return db.sql(query).bind("id", value).map((row, meta) -> row.get(0, Long.class)).one().block();
    }
}
