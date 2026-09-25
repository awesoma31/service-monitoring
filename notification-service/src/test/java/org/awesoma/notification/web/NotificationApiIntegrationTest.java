package org.awesoma.notification.web;

import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.atomic.AtomicLong;
import org.awesoma.notification.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class NotificationApiIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(20_000);

    @Autowired private WebTestClient http;

    @Test
    void anIncidentEventAlertsEveryEnabledChannelOfTheProject() {
        long projectId = IDS.incrementAndGet();
        long incidentId = IDS.incrementAndGet();
        channel(projectId, "EMAIL", "ops@example.com");
        long silenced = channel(projectId, "WEBHOOK", "https://hooks.example.com/x");
        http.put().uri("/api/v1/channels/{id}", silenced)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"target":"https://hooks.example.com/x","enabled":false}""")
                .exchange().expectStatus().isOk();

        incidentChanged(incidentId, projectId, "OPENED");
        incidentChanged(incidentId, projectId, "RESOLVED");

        http.get().uri("/api/v1/incidents/{id}/notifications", incidentId).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.content[0].channel_type").isEqualTo("EMAIL")
                .jsonPath("$.content[0].target").isEqualTo("ops@example.com")
                .jsonPath("$.content[0].status").isEqualTo("PENDING");
    }

    @Test
    void notificationsOfAnUnknownIncidentAreNotFound() {
        when(monitorService.incidentExists(999_999L)).thenReturn(false);

        http.get().uri("/api/v1/incidents/{id}/notifications", 999_999).exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deletingAProjectRemovesItsChannelsAndTheirNotifications() {
        long projectId = IDS.incrementAndGet();
        long incidentId = IDS.incrementAndGet();
        long channelId = channel(projectId, "EMAIL", "gone@example.com");
        incidentChanged(incidentId, projectId, "OPENED");

        http.delete().uri("/internal/projects/{id}/channels", projectId).exchange()
                .expectStatus().isNoContent();

        http.get().uri("/api/v1/channels/{id}", channelId).exchange().expectStatus().isNotFound();
        http.get().uri("/api/v1/incidents/{id}/notifications", incidentId).exchange()
                .expectBody().jsonPath("$.content.length()").isEqualTo(0);
    }

    @Test
    void anIncompleteEventIsRejected() {
        http.post().uri("/internal/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"incident_id":1,"kind":"OPENED"}""")
                .exchange()
                .expectStatus().isBadRequest();
    }

    private void incidentChanged(long incidentId, long projectId, String kind) {
        http.post().uri("/internal/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"incident_id\":%d,\"project_id\":%d,\"kind\":\"%s\"}"
                        .formatted(incidentId, projectId, kind))
                .exchange()
                .expectStatus().isNoContent();
    }

    private long channel(long projectId, String type, String target) {
        return http.post().uri("/api/v1/projects/{id}/channels", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"type\":\"%s\",\"target\":\"%s\"}".formatted(type, target))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody().get("id").asLong();
    }
}
