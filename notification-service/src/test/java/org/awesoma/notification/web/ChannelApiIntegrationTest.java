package org.awesoma.notification.web;

import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.atomic.AtomicLong;
import org.awesoma.notification.support.AbstractIntegrationTest;
import org.awesoma.notification.web.dto.ChannelResponse;
import org.awesoma.notification.web.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class ChannelApiIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicLong PROJECTS = new AtomicLong(10_000);


    @Test
    void rejectsUnauthenticatedRequestsReadOnlyWritesAndForeignProjects() {
        http.mutate().defaultHeaders(headers -> headers.remove("Authorization")).build()
                .get().uri("/api/v1/projects/1/channels").exchange().expectStatus().isUnauthorized();
        http.post().uri("/api/v1/projects/1/channels")
                .headers(headers -> headers.setBearerAuth(tokens.issue("1", "VIEWER").getTokenValue()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"type\":\"EMAIL\",\"target\":\"ops@example.com\"}")
                .exchange().expectStatus().isForbidden();
        Mockito.when(accessClient.allowed("project", 123L, 1L, false)).thenReturn(false);
        http.get().uri("/api/v1/projects/123/channels").exchange().expectStatus().isForbidden();
    }

    @Test
    void createsAChannelUnderItsProject() {
        long projectId = PROJECTS.incrementAndGet();

        http.post().uri("/api/v1/projects/{id}/channels", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"type":"EMAIL","target":"ops@example.com"}""")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", "/api/v1/channels/\\d+")
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.project_id").isEqualTo(projectId)
                .jsonPath("$.enabled").isEqualTo(true);
    }

    @Test
    void theSameDestinationCannotBeRegisteredTwice() {
        long projectId = PROJECTS.incrementAndGet();
        create(projectId, "WEBHOOK", "https://hooks.example.com/a");

        http.post().uri("/api/v1/projects/{id}/channels", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"type":"WEBHOOK","target":"https://hooks.example.com/a"}""")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.title").isEqualTo("Conflicting state");
    }

    @Test
    void aChannelCanBeSilencedRetargetedAndDeleted() {
        long channelId = create(PROJECTS.incrementAndGet(), "EMAIL", "old@example.com");

        http.put().uri("/api/v1/channels/{id}", channelId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"target":"new@example.com","enabled":false}""")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.target").isEqualTo("new@example.com")
                .jsonPath("$.enabled").isEqualTo(false);

        http.delete().uri("/api/v1/channels/{id}", channelId).exchange().expectStatus().isNoContent();
        http.get().uri("/api/v1/channels/{id}", channelId).exchange().expectStatus().isNotFound();
    }

    @Test
    void listingIsScopedToTheProject() {
        long projectId = PROJECTS.incrementAndGet();
        create(projectId, "EMAIL", "a@example.com");
        create(projectId, "TELEGRAM", "@oncall");
        create(PROJECTS.incrementAndGet(), "EMAIL", "other@example.com");

        http.get().uri("/api/v1/projects/{id}/channels", projectId).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Total-Count", "2")
                .expectHeader().valueEquals("X-Has-Next", "false")
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2);

        http.get().uri("/api/v1/projects/{id}/channels?size=1", projectId).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Total-Count", "2")
                .expectHeader().valueEquals("X-Has-Next", "true")
                .expectBody().jsonPath("$.length()").isEqualTo(1);

        http.get().uri("/api/v1/projects/{id}/channels", projectId)
                .accept(MediaType.APPLICATION_NDJSON).exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
                .expectBodyList(ChannelResponse.class).hasSize(2);
    }

    @Test
    void aChannelOfAnUnknownProjectIsNotFound() {
        when(monitorService.projectExists(999_999L)).thenReturn(false);

        http.post().uri("/api/v1/projects/{id}/channels", 999_999)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"type":"EMAIL","target":"ops@example.com"}""")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void aChannelCannotBeCreatedWhileProjectsCannotBeChecked() {
        long projectId = PROJECTS.incrementAndGet();
        when(monitorService.projectExists(projectId)).thenThrow(
                new ServiceUnavailableException("monitor-service", new IllegalStateException("down")));

        http.post().uri("/api/v1/projects/{id}/channels", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"type":"EMAIL","target":"ops@example.com"}""")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.title").isEqualTo("Service unavailable");
    }

    @Test
    void anInvalidChannelIsRejectedWithItsFieldNames() {
        http.post().uri("/api/v1/projects/{id}/channels", PROJECTS.incrementAndGet())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"type":"EMAIL","target":""}""")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.violations[0].field").isEqualTo("target");
    }

    private long create(long projectId, String type, String target) {
        return http.post().uri("/api/v1/projects/{id}/channels", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"type\":\"%s\",\"target\":\"%s\"}".formatted(type, target))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody().get("id").asLong();
    }
}
