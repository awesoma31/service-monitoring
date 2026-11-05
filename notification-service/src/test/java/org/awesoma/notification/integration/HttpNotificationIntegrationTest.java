package org.awesoma.notification.integration;

import org.awesoma.notification.application.usecase.NotificationEventHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.awesoma.integration.IntegrationEvent;
import org.awesoma.notification.domain.Channel;
import org.awesoma.notification.domain.ChannelType;
import org.awesoma.notification.repository.ChannelRepository;
import org.awesoma.notification.repository.NotificationRepository;
import org.awesoma.notification.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class HttpNotificationIntegrationTest extends AbstractIntegrationTest {
    @Autowired NotificationEventHandler events;
    @Autowired ChannelRepository channels;
    @Autowired NotificationRepository notifications;
    @Autowired JdbcTemplate db;

    @Test
    void httpEnvelopeIsIdempotentValidatedAndLegacyDeleteFencesChannelCreation() {
        long projectId = 771001L;
        long incidentId = 771002L;
        Channel channel = channel(projectId);
        var event = event(projectId, incidentId);
        for (int i = 0; i < 2; i++) {
            http.post().uri("/internal/integration/events")
                    .headers(h -> h.setBearerAuth(tokens.issue("monitor", "SERVICE").getTokenValue()))
                    .bodyValue(event).exchange().expectStatus().isNoContent();
        }
        assertThat(notifications.findAll().stream().filter(n -> n.getIncidentId().equals(incidentId))).hasSize(1);
        http.post().uri("/internal/integration/events")
                .headers(h -> h.setBearerAuth(tokens.issue("monitor", "SERVICE").getTokenValue()))
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"schema_version\":1}")
                .exchange().expectStatus().isBadRequest();
        http.delete().uri("/internal/projects/{id}/channels", projectId)
                .headers(h -> h.setBearerAuth(tokens.issue("monitor", "SERVICE").getTokenValue()))
                .exchange().expectStatus().isNoContent();
        assertThat(channels.findById(channel.getId())).isEmpty();
        http.post().uri("/api/v1/projects/{id}/channels", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"type\":\"EMAIL\",\"target\":\"new@example.com\"}")
                .exchange().expectStatus().isNotFound();
    }

    @Test
    void failingBusinessWriteRollsBackInboxAndCanBeRetried() {
        long projectId = 771003L;
        long incidentId = 771004L;
        channel(projectId);
        var event = event(projectId, incidentId);
        db.execute("ALTER TABLE notifications ADD CONSTRAINT test_reject_notifications CHECK (false) NOT VALID");
        try {
            assertThrows(RuntimeException.class, () -> events.handle(ResourceEventMapper.from(event)).block());
            assertThat(db.queryForObject("SELECT count(*) FROM integration_inbox WHERE event_id = ?",
                    Integer.class, event.eventId())).isZero();
        } finally {
            db.execute("ALTER TABLE notifications DROP CONSTRAINT test_reject_notifications");
        }
        events.handle(ResourceEventMapper.from(event)).block();
        events.handle(ResourceEventMapper.from(event)).block();
        assertThat(notifications.findAll().stream().filter(n -> n.getIncidentId().equals(incidentId))).hasSize(1);
    }

    private IntegrationEvent event(long projectId, long incidentId) {
        return new IntegrationEvent(UUID.randomUUID(), 1, IntegrationEvent.INCIDENT_CHANGED,
                OffsetDateTime.now(), null, projectId, incidentId, "OPENED", null, null);
    }

    private Channel channel(long projectId) {
        Channel channel = new Channel();
        channel.setProjectId(projectId);
        channel.setType(ChannelType.EMAIL);
        channel.setTarget(projectId + "@example.com");
        return channels.saveAndFlush(channel);
    }
}
