package org.awesoma.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.awesoma.security.jwt.JwtTokens;
import org.awesoma.security.support.TestJwtConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Routes come from the config repository. With no instance of the target service registered,
 * a routed path is answered by the circuit breaker fallback, while an unrouted one is simply
 * not found.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestJwtConfiguration.class)
class GatewayRoutingTest {

    @Autowired private WebTestClient http;
    @Autowired private JwtTokens tokens;

    @BeforeEach
    void authenticateRequests() {
        http = http.mutate().defaultHeader("Authorization", "Bearer " + tokens.issue("1", "SUPERVISOR").getTokenValue())
                .build();
    }

    @Test
    void anUnavailableServiceAnswersThroughTheCircuitBreakerFallback() {
        http.get().uri("/api/v1/users").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Service unavailable")
                .jsonPath("$.detail").isEqualTo("user-service is temporarily unavailable, try again later")
                .jsonPath("$.instance").isEqualTo("/api/v1/users");
    }

    @Test
    void eachRouteFallsBackForItsOwnService() {
        http.get().uri("/api/v1/monitors/1/results").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.detail")
                .isEqualTo("check-service is temporarily unavailable, try again later");
        http.post().uri("/api/v1/projects/1/channels").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.detail")
                .isEqualTo("notification-service is temporarily unavailable, try again later");
        String report = "/api/v1/reports/7b0c1a0e-6c1f-4f1e-9d55-2b9f6e3c1a10";
        for (String path : List.of("/api/v1/projects/1/reports", report, report + "/content")) {
            http.get().uri(path).exchange()
                    .expectStatus().isEqualTo(503)
                    .expectBody().jsonPath("$.detail")
                    .isEqualTo("report-service is temporarily unavailable, try again later");
        }
    }

    @Test
    void anOversizedReportUploadIsRejectedBeforeRouting() {
        byte[] body = new byte[12 * 1024 * 1024 + 1];

        http.post().uri("/api/v1/projects/1/reports")
                .header("Content-Type", "multipart/form-data; boundary=x")
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(413);
    }

    @Test
    void aViewerCannotUploadOrDeleteReports() {
        String viewer = tokens.issue("1", "VIEWER").getTokenValue();
        http.post().uri("/api/v1/projects/1/reports").headers(headers -> headers.setBearerAuth(viewer))
                .exchange().expectStatus().isForbidden();
        http.delete().uri("/api/v1/reports/7b0c1a0e-6c1f-4f1e-9d55-2b9f6e3c1a10")
                .headers(headers -> headers.setBearerAuth(viewer))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void theCatchAllRouteComesAfterEveryServiceSpecificOne() {
        JsonNode routes = http.get().uri("/actuator/gateway/routes").exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();

        List<String> ids = new ArrayList<>();
        routes.forEach(route -> ids.add(route.get("route_id").asText()));
        assertThat(ids).last().isEqualTo("monitor-service");
        assertThat(ids).contains(
                "check-service", "notification-service", "report-service",
                "monitor-service-api-docs", "check-service-api-docs", "notification-service-api-docs",
                        "user-service-api-docs", "user-service", "report-service-api-docs");
    }

    @Test
    void theSharedSwaggerUiListsTheDocumentOfEveryService() {
        http.get().uri("/v3/api-docs/swagger-config").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.urls[*].name")
                .value(names -> assertThat(names.toString())
                        .contains("monitor-service", "check-service", "notification-service", "user-service",
                                "report-service"))
                .jsonPath("$.urls[?(@.name == 'monitor-service')].url")
                .isEqualTo(List.of("/v3/api-docs/monitor-service"));

        http.get().uri("/swagger-ui.html").exchange().expectStatus().is3xxRedirection();
    }

    @Test
    void gatewayRejectsMissingOrInvalidTokensBeforeRouting() {
        http.mutate().defaultHeaders(headers -> headers.remove("Authorization")).build()
                .get().uri("/api/v1/projects").exchange().expectStatus().isUnauthorized();
        http.get().uri("/api/v1/projects").headers(headers -> headers.setBearerAuth("invalid"))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void viewerAndOperatorPermissionsAreEnforcedBeforeRouting() {
        http.post().uri("/api/v1/projects/1/channels")
                .headers(headers -> headers.setBearerAuth(tokens.issue("1", "VIEWER").getTokenValue()))
                .exchange().expectStatus().isForbidden();
        http.post().uri("/api/v1/users")
                .headers(headers -> headers.setBearerAuth(tokens.issue("1", "OPERATOR").getTokenValue()))
                .exchange().expectStatus().isForbidden();
        http.get().uri("/api/v1/users")
                .headers(headers -> headers.setBearerAuth(tokens.issue("check-service", "SERVICE").getTokenValue()))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void otherPathsAreNotRouted() {
        http.get().uri("/unknown").exchange().expectStatus().isForbidden();
    }
}
