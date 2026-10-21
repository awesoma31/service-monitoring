package org.awesoma.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtTokensTest {
    private final JwtProperties properties = new JwtProperties(null, null, null, null, null);

    @Test
    void signedTokensCarryAnExpiringIdentityAndRole() {
        var tokens = new JwtTokens(TestKeys.encoder(), properties);
        var issued = tokens.issue("42", "OPERATOR");
        var decoded = TestKeys.decoder(properties).decode(issued.getTokenValue());
        assertThat(decoded.getSubject()).isEqualTo("42");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("OPERATOR");
        assertThat(Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(new JwtConfiguration().jwtAuthenticationConverter().convert(decoded).getAuthorities())
                .extracting("authority").containsExactly("ROLE_OPERATOR");
        var service = tokens.issue("check-service", "SERVICE");
        assertThat(Duration.between(service.getIssuedAt(), service.getExpiresAt())).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void rejectsWrongKeysExpiredTokensWrongIssuersAndWrongAudiences() {
        var forged = new JwtTokens(TestKeys.encoder(TestKeys.generate()), properties).issue("42", "SUPERVISOR");
        assertThatThrownBy(() -> TestKeys.decoder(properties).decode(forged.getTokenValue()))
                .isInstanceOf(JwtException.class);
        for (int invalid = 0; invalid < 3; invalid++) {
            var claims = JwtClaimsSet.builder().subject("42").issuer(invalid == 1 ? "untrusted" : properties.issuer())
                    .audience(List.of(invalid == 2 ? "other-api" : properties.audience()))
                    .issuedAt(Instant.now().minusSeconds(600))
                    .expiresAt(Instant.now().plusSeconds(invalid == 0 ? -120 : 300)).build();
            String token = TestKeys.encoder().encode(JwtEncoderParameters.from(
                    JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
            assertThatThrownBy(() -> TestKeys.decoder(properties).decode(token)).isInstanceOf(JwtException.class);
        }
        assertThatThrownBy(() -> new JwtProperties(null, null, null, Duration.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hashesPasswordsWithDistinctSaltsIncludingLongPasswords() {
        var passwords = PasswordHashing.encoder();
        String plain = "long-password".repeat(15);
        String first = passwords.encode(plain);
        String second = passwords.encode(plain);
        assertThat(first).isNotEqualTo(plain).isNotEqualTo(second);
        assertThat(passwords.matches(plain, first)).isTrue();
        assertThat(passwords.matches("wrong", first)).isFalse();
    }
}
