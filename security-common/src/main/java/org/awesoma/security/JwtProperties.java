package org.awesoma.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("security.jwt")
public record JwtProperties(String issuer, String jwkSetUri, String audience, Duration ttl, Duration serviceTtl) {
    public JwtProperties {
        issuer = issuer == null ? "service-monitoring" : issuer;
        audience = audience == null ? "service-monitoring-api" : audience;
        jwkSetUri = jwkSetUri == null ? "http://localhost:8084/.well-known/jwks.json" : jwkSetUri;
        ttl = ttl == null ? Duration.ofMinutes(15) : ttl;
        serviceTtl = serviceTtl == null ? Duration.ofMinutes(5) : serviceTtl;
        if (ttl.isNegative() || ttl.isZero() || serviceTtl.isNegative() || serviceTtl.isZero()) {
            throw new IllegalArgumentException("JWT lifetimes must be positive");
        }
    }
}
