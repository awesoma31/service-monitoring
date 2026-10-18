package org.awesoma.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

public final class JwtValidation {
    private JwtValidation() {}
    public static OAuth2TokenValidator<Jwt> validator(JwtProperties properties) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtClaimValidator<java.util.List<String>>("aud",
                        values -> values != null && values.contains(properties.audience())),
                new JwtClaimValidator<java.time.Instant>("exp", value -> value != null));
    }
}
