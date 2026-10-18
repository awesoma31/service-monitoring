package org.awesoma.monitoring.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.awesoma.monitoring.config.OpenApiConfig;
import org.awesoma.monitoring.integration.CheckHistoryClient;
import org.awesoma.monitoring.integration.CheckHistoryClientFallbackFactory;
import org.awesoma.monitoring.integration.NotificationClient;
import org.awesoma.monitoring.integration.NotificationClientFallbackFactory;
import org.awesoma.monitoring.service.ServiceHealthService;
import org.awesoma.monitoring.web.controller.ServiceHealthController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Real Feign, discovery, fallback and circuit breakers; only downstream HTTP is substituted. */
@SpringBootTest(classes = ServiceHealthCircuitBreakerTest.TestApplication.class, properties = {
    "resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state=1h"
})
@AutoConfigureMockMvc
class ServiceHealthCircuitBreakerTest {

    private static final Downstream CHECKS = new Downstream();
    private static final Downstream NOTIFICATIONS = new Downstream();

    @Autowired private MockMvc http;
    @Autowired private CircuitBreakerRegistry circuitBreakers;

    @DynamicPropertySource
    static void downstreamAddresses(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.check-service[0].uri", CHECKS::url);
        registry.add("spring.cloud.discovery.client.simple.instances.notification-service[0].uri", NOTIFICATIONS::url);
    }

    @BeforeEach
    void reset() {
        CHECKS.reset();
        NOTIFICATIONS.reset();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @AfterAll
    static void stopServers() {
        CHECKS.server.stop(0);
        NOTIFICATIONS.server.stop(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"check-service", "notification-service"})
    void healthyServiceAnswersThroughFeign(String service) throws Exception {
        http.perform(get(path(service)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value(service))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.circuit_breaker_state").value("CLOSED"));
        assertThat(downstream(service).calls.get()).isEqualTo(1);
    }

    @Test
    void openApiExposesBothHealthEndpointsForTheGatewaySwaggerUi() throws Exception {
        var document = http.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
        for (String service : new String[] {"check-service", "notification-service"}) {
            String operation = "$.paths['" + path(service) + "'].get";
            document.andExpect(jsonPath(operation + ".tags[0]").value("Service health"));
            for (String code : new String[] {"200", "503"}) {
                document.andExpect(jsonPath(operation + ".responses['" + code
                                + "'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ServiceHealthResponse"));
            }
        }
        document.andExpect(jsonPath("$.components.schemas.ServiceHealthResponse.properties.circuit_breaker_state")
                .exists());
        document.andExpect(jsonPath("$.servers[0].url").value("/"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"check-service", "notification-service"})
    void failuresOpenTheCircuitAndFurtherRequestsDoNotReachTheService(String service) throws Exception {
        openCircuit(service);

        http.perform(get(path(service)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.service").value(service))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.circuit_breaker_state").value("OPEN"));
        assertThat(downstream(service).calls.get()).isEqualTo(5);

        String other = service.equals("check-service") ? "notification-service" : "check-service";
        http.perform(get(path(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.circuit_breaker_state").value("CLOSED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"check-service", "notification-service"})
    void twoSuccessfulTrialCallsCloseTheCircuitAfterRecovery(String service) throws Exception {
        openCircuit(service);
        downstream(service).available.set(true);
        // Advance the open-state wait deterministically, without a timer-dependent test.
        breaker(service).transitionToHalfOpenState();

        http.perform(get(path(service)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.circuit_breaker_state").value("HALF_OPEN"));
        http.perform(get(path(service)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.circuit_breaker_state").value("CLOSED"));
        assertThat(downstream(service).calls.get()).isEqualTo(7);
    }

    private void openCircuit(String service) throws Exception {
        downstream(service).available.set(false);
        for (int call = 0; call < 5; call++) {
            http.perform(get(path(service)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.fallback").value(true));
        }
        assertThat(breaker(service).getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    private CircuitBreaker breaker(String service) {
        return circuitBreakers.circuitBreaker(service.equals("check-service")
                ? "CheckHistoryClienthealth" : "NotificationClienthealth");
    }

    private static Downstream downstream(String service) {
        return service.equals("check-service") ? CHECKS : NOTIFICATIONS;
    }

    private static String path(String service) {
        return "/api/v1/services/" + service + "/health";
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, LiquibaseAutoConfiguration.class
    })
    @EnableFeignClients(clients = {CheckHistoryClient.class, NotificationClient.class})
    @Import({ServiceHealthController.class, ServiceHealthService.class, OpenApiConfig.class,
        CheckHistoryClientFallbackFactory.class, NotificationClientFallbackFactory.class})
    static class TestApplication {}

    private static class Downstream {
        final AtomicBoolean available = new AtomicBoolean(true);
        final AtomicInteger calls = new AtomicInteger();
        final HttpServer server;

        Downstream() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/actuator/health", exchange -> {
                    calls.incrementAndGet();
                    boolean up = available.get();
                    byte[] body = (up ? "{\"status\":\"UP\"}" : "{\"status\":\"DOWN\"}")
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(up ? 200 : 503, body.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                });
                server.start();
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void reset() {
            available.set(true);
            calls.set(0);
        }
    }
}
