package org.awesoma.check.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.awesoma.check.client.MonitorClient;
import org.awesoma.check.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The real Feign client, with no monitor-service registered: every call fails, the fallback
 * answers instead, and the circuit opens once enough calls have failed.
 */
@AutoConfigureWebTestClient
class CircuitBreakerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MonitorClient monitorService;
    @Autowired private CircuitBreakerRegistry circuitBreakers;
    @Autowired private WebTestClient http;

    @Test
    void aCheckPassIsSkippedAndTheCircuitOpensWhileMonitorServiceIsDown() {
        for (int call = 0; call < 6; call++) {
            assertThat(monitorService.due(10)).isEmpty();
        }

        assertThat(circuitBreakers.getAllCircuitBreakers())
                .filteredOn(breaker -> breaker.getName().contains("due"))
                .singleElement()
                .extracting(CircuitBreaker::getState)
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void historyAnswersServiceUnavailableWhenMonitorsCannotBeChecked() {
        http.get().uri("/api/v1/monitors/{id}/results", 1).exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Service unavailable")
                .jsonPath("$.detail").isEqualTo("monitor-service is temporarily unavailable");
    }
}
