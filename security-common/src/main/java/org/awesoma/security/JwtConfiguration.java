package org.awesoma.security;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Duration;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {
    @Bean
    public OpenApiCustomizer bearerTokenDocumentation() {
        return openApi -> {
            if (openApi.getComponents() == null) openApi.setComponents(new Components());
            openApi.getComponents().addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
            openApi.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
            if (openApi.getPaths() != null && openApi.getPaths().containsKey("/api/v1/auth/login")) {
                openApi.getPaths().get("/api/v1/auth/login").getPost().setSecurity(List.of());
            }
        };
    }
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        var decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .restOperations(new RestTemplate(factory))
                .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(JwtValidation.validator(properties));
        return token -> {
            try {
                return decoder.decode(token);
            } catch (BadJwtException invalid) {
                throw invalid;
            } catch (JwtException unavailable) {
                throw new IdentityServiceUnavailableException(unavailable);
            }
        };
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
