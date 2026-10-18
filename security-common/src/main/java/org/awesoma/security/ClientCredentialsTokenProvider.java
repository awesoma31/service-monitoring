package org.awesoma.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Dedicated HTTP client avoids recursive invocation of Feign's authentication interceptor. */
public class ClientCredentialsTokenProvider implements ServiceTokenProvider {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json;
    private final URI endpoint;
    private final String clientId;
    private final String clientSecret;
    private String cached;
    private Instant renewAt = Instant.MIN;

    public ClientCredentialsTokenProvider(ObjectMapper json, String endpoint, String clientId, String clientSecret) {
        this.json = json;
        this.endpoint = URI.create(endpoint);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }
    @Override
    public synchronized String token() {
        if (cached != null && Instant.now().isBefore(renewAt)) return cached;
        try {
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                            Map.of("client_id", clientId, "client_secret", clientSecret))))
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Service authentication failed");
            var body = json.readTree(response.body());
            long seconds = body.path("expires_in").asLong();
            String token = body.path("access_token").asText();
            if (seconds <= 0 || token.isBlank() || !"Bearer".equals(body.path("token_type").asText())) {
                throw new IllegalStateException("Invalid service token response");
            }
            cached = token;
            renewAt = Instant.now().plusSeconds(seconds).minusSeconds(Math.min(30, seconds / 10));
            return cached;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service authentication is unavailable");
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service authentication is unavailable");
        }
    }
}
