package org.awesoma.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Keys are generated in the test JVM only; production clients have no signing key. */
public final class TestKeys {
    private static final RSAKey KEY = generate();
    private TestKeys() {}

    public static RSAKey generate() {
        try { return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate(); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    public static JwtEncoder encoder() { return encoder(KEY); }
    public static JwtEncoder encoder(RSAKey key) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }
    public static JwtDecoder decoder(JwtProperties properties) {
        try {
            var decoder = NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(JwtValidation.validator(properties));
            return decoder;
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
}
