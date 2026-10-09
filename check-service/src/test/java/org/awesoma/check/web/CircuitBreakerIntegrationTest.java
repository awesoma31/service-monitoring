package org.awesoma.check.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awesoma.check.client.MonitorClient;
import org.awesoma.check.domain.MonitorTarget;
import org.awesoma.check.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The real Feign client calls a controllable HTTP substitute for monitor-service. It starts
 * unavailable so the fallback answers and the circuit opens; a recovery test then brings it
 * back and verifies the half-open trial calls close the circuit.
 */
@AutoConfigureWebTestClient
class CircuitBreakerIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicBoolean MONITOR_SERVICE_AVAILABLE = new AtomicBoolean();
    private static final HttpServer MONITOR_SERVICE = startMonitorService();
    private static final String DUE_RESPONSE = """
            [{
              "monitor_id": 42,
              "url": "https://example.com/health",
              "http_method": "GET",
              "timeout_ms": 1000,
              "expected_status": 200,
              "claim_token": "00000000-0000-0000-0000-000000000042"
            }]
            """;

    @Autowired private MonitorClient monitorService;
    @Autowired private CircuitBreakerRegistry circuitBreakers;
    @Autowired private WebTestClient http;

    @DynamicPropertySource
    static void monitorServiceProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.cloud.discovery.client.simple.instances.monitor-service[0].uri",
                () -> "http://127.0.0.1:" + MONITOR_SERVICE.getAddress().getPort());
        registry.add(
                "resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state",
                () -> "100ms");
    }

    @BeforeEach
    void resetCircuitBreakers() {
        MONITOR_SERVICE_AVAILABLE.set(false);
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @AfterAll
    static void stopMonitorService() {
        MONITOR_SERVICE.stop(0);
    }

    @Test
    void aCheckPassIsSkippedAndTheCircuitOpensWhileMonitorServiceIsDown() {
        for (int call = 0; call < 6; call++) {
            assertThat(monitorService.due(10, 4)).isEmpty();
        }

        assertThat(circuitBreakers.getAllCircuitBreakers())
                .filteredOn(breaker -> breaker.getName().contains("due"))
                .singleElement()
                .extracting(CircuitBreaker::getState)
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void circuitClosesAfterMonitorServiceRecovers() throws InterruptedException {
        for (int call = 0; call < 6; call++) {
            assertThat(monitorService.due(10, 4)).isEmpty();
        }
        CircuitBreaker breaker = dueCircuitBreaker();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        MONITOR_SERVICE_AVAILABLE.set(true);
        awaitState(breaker, CircuitBreaker.State.HALF_OPEN);

        assertRecoveredResponse(monitorService.due(10, 4));
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        assertRecoveredResponse(monitorService.due(10, 4));
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void historyAnswersServiceUnavailableWhenMonitorsCannotBeChecked() {
        http.get().uri("/api/v1/monitors/{id}/results", 1).exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Service unavailable")
                .jsonPath("$.detail").isEqualTo("monitor-service is temporarily unavailable");
    }

    private static HttpServer startMonitorService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/monitors", CircuitBreakerIntegrationTest::respond);
            server.start();
            return server;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        if (!MONITOR_SERVICE_AVAILABLE.get()) {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
        }

        byte[] response = DUE_RESPONSE.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private CircuitBreaker dueCircuitBreaker() {
        for (CircuitBreaker breaker : circuitBreakers.getAllCircuitBreakers()) {
            if (breaker.getName().contains("due")) {
                return breaker;
            }
        }
        throw new AssertionError("Circuit Breaker for MonitorClient.due was not created");
    }

    private static void awaitState(CircuitBreaker breaker, CircuitBreaker.State expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline && breaker.getState() != expected) {
            Thread.sleep(10);
        }
        assertThat(breaker.getState()).isEqualTo(expected);
    }

    private static void assertRecoveredResponse(List<MonitorTarget> targets) {
        assertThat(targets)
                .singleElement()
                .extracting(MonitorTarget::monitorId)
                .isEqualTo(42L);
    }
}
