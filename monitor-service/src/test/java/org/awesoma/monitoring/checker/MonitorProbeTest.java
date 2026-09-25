package org.awesoma.monitoring.checker;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import org.awesoma.monitoring.domain.enums.CheckResultType;
import org.awesoma.monitoring.domain.enums.HttpMethod;
import org.awesoma.monitoring.domain.model.MonitorTarget;
import org.awesoma.monitoring.domain.model.ProbeOutcome;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Probes a real HTTP server from the JDK, so the outcomes come from actual sockets. */
class MonitorProbeTest {

    private static HttpServer server;
    private static String baseUrl;

    private final MonitorProbe probe = new MonitorProbe();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> respond(exchange, 200));
        server.createContext("/teapot", exchange -> respond(exchange, 418));
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200);
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status)
            throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    @Test
    void reportsSuccessWhenTheStatusMatchesWhatIsExpected() {
        ProbeOutcome outcome = probe.probe(target(baseUrl + "/ok", 200, 2000));

        assertThat(outcome.result()).isEqualTo(CheckResultType.SUCCESS);
        assertThat(outcome.httpStatus()).isEqualTo(200);
        assertThat(outcome.responseMs()).isNotNegative();
        assertThat(outcome.errorMessage()).isNull();
        assertThat(outcome.isFailure()).isFalse();
    }

    @Test
    void reportsBadStatusWhenTheAnswerDiffersFromTheExpectedOne() {
        ProbeOutcome outcome = probe.probe(target(baseUrl + "/teapot", 200, 2000));

        assertThat(outcome.result()).isEqualTo(CheckResultType.BAD_STATUS);
        assertThat(outcome.httpStatus()).isEqualTo(418);
        assertThat(outcome.errorMessage()).contains("200").contains("418");
    }

    @Test
    void anExpectedNonSuccessStatusStillCountsAsSuccess() {
        ProbeOutcome outcome = probe.probe(target(baseUrl + "/teapot", 418, 2000));

        assertThat(outcome.result()).isEqualTo(CheckResultType.SUCCESS);
    }

    @Test
    void reportsTimeoutWhenTheServerAnswersTooLate() {
        ProbeOutcome outcome = probe.probe(target(baseUrl + "/slow", 200, 300));

        assertThat(outcome.result()).isEqualTo(CheckResultType.TIMEOUT);
        assertThat(outcome.httpStatus()).isNull();
        assertThat(outcome.severity()).isEqualTo(org.awesoma.monitoring.domain.enums.Severity.HIGH);
    }

    @Test
    void reportsConnectionErrorWhenNothingIsListening() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        ProbeOutcome outcome = probe.probe(target("http://127.0.0.1:" + closedPort + "/", 200, 1000));

        assertThat(outcome.result()).isEqualTo(CheckResultType.CONNECTION_ERROR);
        assertThat(outcome.errorMessage()).isNotBlank();
    }

    private MonitorTarget target(String url, int expectedStatus, int timeoutMs) {
        return new MonitorTarget(1L, url, HttpMethod.GET, timeoutMs, expectedStatus);
    }
}
