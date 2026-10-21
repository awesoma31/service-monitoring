package org.awesoma.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Configuration(proxyBeanMethods = false)
@Import(JwtConfiguration.class)
public class ReactiveSecurityConfiguration {
    @Bean
    public SecurityWebFilterChain apiSecurity(ServerHttpSecurity http, JwtDecoder decoder,
            JwtAuthenticationConverter converter, ObjectMapper json) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(rules -> rules
                        .pathMatchers("/.well-known/jwks.json", "/actuator/health/**", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/webjars/**", "/fallback/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/register").hasRole("SUPERVISOR")
                        .pathMatchers("/internal/**").hasRole("SERVICE")
                        .pathMatchers(HttpMethod.GET, "/api/v1/users/*")
                                .hasAnyRole("SUPERVISOR", "OPERATOR", "VIEWER")
                        .pathMatchers("/api/v1/users/**", "/api/v1/services/**", "/actuator/**")
                                .hasRole("SUPERVISOR")
                        .pathMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole("SUPERVISOR", "OPERATOR", "VIEWER")
                        .pathMatchers("/api/v1/**").hasAnyRole("SUPERVISOR", "OPERATOR")
                        .anyExchange().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((exchange, error) ->
                                problem(exchange, json, authenticationStatus(error)))
                        .accessDeniedHandler((exchange, error) -> problem(exchange, json, HttpStatus.FORBIDDEN)))
                .oauth2ResourceServer(resource -> resource
                        .authenticationEntryPoint((exchange, error) ->
                                problem(exchange, json, authenticationStatus(error)))
                        .accessDeniedHandler((exchange, error) -> problem(exchange, json, HttpStatus.FORBIDDEN))
                        .jwt(jwt -> jwt
                                .jwtDecoder(token -> Mono.fromCallable(() -> decoder.decode(token))
                                        .subscribeOn(Schedulers.boundedElastic()))
                                .jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(converter))))
                .build();
    }

    private HttpStatus authenticationStatus(AuthenticationException error) {
        return error instanceof IdentityServiceUnavailableException
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNAUTHORIZED;
    }

    private Mono<Void> problem(ServerWebExchange exchange, ObjectMapper json, HttpStatus status) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set("WWW-Authenticate", "Bearer");
        }
        byte[] body;
        try {
            body = json.writeValueAsBytes(Map.of("type", "about:blank", "title", status.getReasonPhrase(),
                    "status", status.value(), "detail", status == HttpStatus.SERVICE_UNAVAILABLE
                            ? "Identity service is unavailable" : status == HttpStatus.UNAUTHORIZED
                            ? "A valid Bearer token is required" : "Insufficient permissions"));
        } catch (JsonProcessingException error) {
            return Mono.error(error);
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
