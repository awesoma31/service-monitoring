package org.awesoma.security;

import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

public class JwtTokens {
    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public JwtTokens(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public Jwt issue(String subject, String role) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(properties.issuer()).subject(subject)
                .audience(List.of(properties.audience()))
                .issuedAt(now).expiresAt(now.plus("SERVICE".equals(role) ? properties.serviceTtl() : properties.ttl()))
                .claim("roles", List.of(role)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims));
    }
}
