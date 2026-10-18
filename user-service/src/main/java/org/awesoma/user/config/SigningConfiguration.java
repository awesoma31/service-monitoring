package org.awesoma.user.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.file.Path;
import org.awesoma.security.JwtProperties;
import org.awesoma.security.JwtTokens;
import org.awesoma.security.JwtValidation;
import org.awesoma.user.security.SigningKeyStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
public class SigningConfiguration {
    @Bean
    public RSAKey signingKey(@Value("${security.jwt.key-file:./keys/signing-key.json}") String file) {
        return new SigningKeyStore().loadOrCreate(Path.of(file));
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey key) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    public JwtTokens jwtTokens(JwtEncoder encoder, JwtProperties properties) {
        return new JwtTokens(encoder, properties);
    }

    @Bean
    @Primary
    public JwtDecoder localJwtDecoder(RSAKey key, JwtProperties properties) throws Exception {
        var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidation.validator(properties));
        return decoder;
    }
}
