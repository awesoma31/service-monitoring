package org.awesoma.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ClientCredentialsTokenProviderTest {
    @Test
    void exchangesCredentialsOnceAndCachesTheToken() throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var json = new ObjectMapper();
        server.createContext("/token", exchange -> {
            calls.incrementAndGet();
            var body = json.readTree(exchange.getRequestBody());
            assertThat(body.get("client_id").asText()).isEqualTo("check-service");
            assertThat(body.get("client_secret").asText()).isEqualTo("private-client-secret");
            byte[] response = "{\"access_token\":\"signed-token\",\"token_type\":\"Bearer\",\"expires_in\":300}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var provider = new ClientCredentialsTokenProvider(json, "http://127.0.0.1:" + server.getAddress().getPort() + "/token",
                    "check-service", "private-client-secret");
            assertThat(provider.token()).isEqualTo("signed-token");
            assertThat(provider.token()).isEqualTo("signed-token");
            assertThat(calls.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test
    void failedAuthenticationDoesNotExposeCredentialsOrGrantAccess() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> { exchange.sendResponseHeaders(401, -1); exchange.close(); });
        server.start();
        try {
            var provider = new ClientCredentialsTokenProvider(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/token", "unknown", "private-secret");
            assertThatThrownBy(provider::token).isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("503").hasMessageNotContaining("private-secret");
        } finally { server.stop(0); }
    }
}
