package org.awesoma.check.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.awesoma.check.client.MonitorClient;
import org.awesoma.check.domain.CheckResult;
import org.awesoma.check.domain.ProbeOutcome;
import org.awesoma.check.repository.CheckResultRepository;
import org.awesoma.check.support.AbstractIntegrationTest;
import org.awesoma.check.web.dto.CheckResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
class CheckHistoryApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired private CheckResultRepository results;
    @MockitoBean private MonitorClient monitorService;

    @BeforeEach
    void everyMonitorExistsUnlessStatedOtherwise() {
        when(monitorService.exists(anyLong())).thenReturn(true);
    }

    @Test
    void historyRequiresAuthenticationAndAccessToTheMonitor() {
        http.mutate().defaultHeaders(headers -> headers.remove("Authorization")).build()
                .get().uri("/api/v1/monitors/1/results").exchange().expectStatus().isUnauthorized();
        when(accessClient.allowed("monitor", 123L, 1L, false)).thenReturn(false);
        http.get().uri("/api/v1/monitors/123/results").exchange().expectStatus().isForbidden();
    }

    @Test
    void historyScrollsNewestFirstWithoutATotal() {
        long monitorId = 1_001;
        OffsetDateTime now = OffsetDateTime.now();
        store(monitorId, ProbeOutcome.success(10, 200), now.minusMinutes(3));
        store(monitorId, ProbeOutcome.connectionError(5, "refused"), now.minusMinutes(2));
        store(monitorId, ProbeOutcome.success(12, 200), now.minusMinutes(1));

        http.get().uri("/api/v1/monitors/{id}/results?size=2", monitorId).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Has-Next", "true")
                .expectHeader().doesNotExist("X-Total-Count")
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].result").isEqualTo("SUCCESS")
                .jsonPath("$[1].result").isEqualTo("CONNECTION_ERROR")
                .jsonPath("$[1].error_message").isEqualTo("refused");

        http.get().uri("/api/v1/monitors/{id}/results?size=2&page=1", monitorId).exchange()
                .expectHeader().valueEquals("X-Has-Next", "false")
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    void historyCanAlsoBeReadAsAnNdjsonStream() {
        long monitorId = 1_003;
        OffsetDateTime now = OffsetDateTime.now();
        store(monitorId, ProbeOutcome.success(10, 200), now.minusMinutes(1));
        store(monitorId, ProbeOutcome.success(12, 200), now);

        http.get().uri("/api/v1/monitors/{id}/results", monitorId)
                .accept(MediaType.APPLICATION_NDJSON).exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
                .expectBodyList(CheckResultResponse.class).hasSize(2);
    }

    @Test
    void anUnknownMonitorIsNotFound() {
        when(monitorService.exists(999_999L)).thenReturn(false);

        http.get().uri("/api/v1/monitors/{id}/results", 999_999).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.title").isEqualTo("Resource not found");
    }

    @Test
    void aPageAboveTheCeilingIsRejected() {
        http.get().uri("/api/v1/monitors/{id}/results?size=51", 1).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.violations[0].field").isEqualTo("size");
    }

    @Test
    void aNonNumericMonitorIdIsABadRequest() {
        http.get().uri("/api/v1/monitors/abc/results").exchange().expectStatus().isBadRequest();
    }

    @Test
    void theHistoryOfADeletedMonitorIsRemovedOnRequest() {
        long monitorId = 1_002;
        store(monitorId, ProbeOutcome.success(10, 200), OffsetDateTime.now());

        http.delete().uri("/internal/results?monitor_id={id}", monitorId)
                .headers(headers -> headers.setBearerAuth(tokens.issue("test-check", "SERVICE").getTokenValue())).exchange()
                .expectStatus().isNoContent();

        http.get().uri("/api/v1/monitors/{id}/results", monitorId).exchange()
                .expectBody().jsonPath("$.length()").isEqualTo(0);
    }

    private void store(long monitorId, ProbeOutcome outcome, OffsetDateTime at) {
        results.save(CheckResult.of(monitorId, outcome, at)).block();
    }
}
