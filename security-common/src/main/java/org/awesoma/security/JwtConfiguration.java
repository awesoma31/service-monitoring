package org.awesoma.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {
    @Bean
    public org.springdoc.core.customizers.OpenApiCustomizer bearerTokenDocumentation() {
        return openApi -> {
            if (openApi.getComponents() == null) openApi.setComponents(new io.swagger.v3.oas.models.Components());
            openApi.getComponents().addSecuritySchemes("bearerAuth", new io.swagger.v3.oas.models.security.SecurityScheme()
                    .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
            openApi.addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("bearerAuth"));
            if (openApi.getPaths() != null && openApi.getPaths().containsKey("/api/v1/auth/login")) {
                openApi.getPaths().get("/api/v1/auth/login").getPost().setSecurity(java.util.List.of());
            }
        };
    }
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(2));
        factory.setReadTimeout(java.time.Duration.ofSeconds(3));
        var decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .restOperations(new org.springframework.web.client.RestTemplate(factory))
                .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(JwtValidation.validator(properties));
        return token -> {
            try {
                return decoder.decode(token);
            } catch (org.springframework.security.oauth2.jwt.BadJwtException invalid) {
                throw invalid;
            } catch (org.springframework.security.oauth2.jwt.JwtException unavailable) {
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
