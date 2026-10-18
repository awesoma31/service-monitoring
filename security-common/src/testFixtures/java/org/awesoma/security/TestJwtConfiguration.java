package org.awesoma.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

@TestConfiguration(proxyBeanMethods = false)
public class TestJwtConfiguration {
    @Bean @Primary public JwtDecoder testJwtDecoder(JwtProperties properties) { return TestKeys.decoder(properties); }
    @Bean @Primary public JwtEncoder testJwtEncoder() { return TestKeys.encoder(); }
    @Bean public JwtTokens jwtTokens(JwtEncoder encoder, JwtProperties properties) { return new JwtTokens(encoder, properties); }
    @Bean @Primary public ServiceTokenProvider testServiceTokens(JwtTokens tokens,
            @Value("${spring.application.name:test-service}") String name) {
        return () -> tokens.issue(name, "SERVICE").getTokenValue();
    }
}
