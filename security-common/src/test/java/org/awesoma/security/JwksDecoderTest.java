package org.awesoma.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.JWKSet;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JwksDecoderTest {
    @Test
    void verifiesAUserServiceTokenOverHttpWithoutPrivateKeyAndCachesThePublicKey() throws Exception {
        var key = TestKeys.generate();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/keys", exchange -> {
            byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var properties = new JwtProperties(null, "http://127.0.0.1:" + server.getAddress().getPort() + "/keys", null, null, null);
            var token = new JwtTokens(TestKeys.encoder(key), properties).issue("12", "OPERATOR");
            var decoder = new JwtConfiguration().jwtDecoder(properties);
            assertThat(decoder.decode(token.getTokenValue()).getSubject()).isEqualTo("12");
            server.stop(0);
            assertThat(decoder.decode(token.getTokenValue()).getSubject()).isEqualTo("12");
        } finally { server.stop(0); }
    }

    @Test
    void unavailableJwksIsReportedAsAnOutageAndMalformedTokensAsInvalidCredentials() {
        var properties = new JwtProperties(null, "http://127.0.0.1:9/keys", null, null, null);
        var decoder = new JwtConfiguration().jwtDecoder(properties);
        var token = new JwtTokens(TestKeys.encoder(), properties).issue("12", "OPERATOR");
        assertThatThrownBy(() -> decoder.decode(token.getTokenValue())).isInstanceOf(IdentityServiceUnavailableException.class);
        assertThatThrownBy(() -> decoder.decode("invalid"))
                .isInstanceOf(org.springframework.security.oauth2.jwt.BadJwtException.class);
    }
}
