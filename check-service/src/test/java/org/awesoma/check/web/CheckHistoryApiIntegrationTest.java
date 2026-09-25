package org.awesoma.check.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.awesoma.check.client.MonitorClient;
import org.awesoma.check.domain.CheckResult;
import org.awesoma.check.domain.ProbeOutcome;
import org.awesoma.check.repository.CheckResultRepository;
import org.awesoma.check.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
class CheckHistoryApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired private WebTestClient http;
    @Autowired private CheckResultRepository results;
    @MockitoBean private MonitorClient monitorService;

    @BeforeEach
    void everyMonitorExistsUnlessStatedOtherwise() {
        when(monitorService.exists(anyLong())).thenReturn(true);
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
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.content[0].result").isEqualTo("SUCCESS")
                .jsonPath("$.content[1].result").isEqualTo("CONNECTION_ERROR")
                .jsonPath("$.content[1].error_message").isEqualTo("refused")
                .jsonPath("$.last").isEqualTo(false)
                // A slice reports whether more rows follow, never how many exist.
                .jsonPath("$.total_elements").doesNotExist();

        http.get().uri("/api/v1/monitors/{id}/results?size=2&page=1", monitorId).exchange()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.last").isEqualTo(true);
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

    private void store(long monitorId, ProbeOutcome outcome, OffsetDateTime at) {
        results.save(CheckResult.of(monitorId, outcome, at)).block();
    }
}
