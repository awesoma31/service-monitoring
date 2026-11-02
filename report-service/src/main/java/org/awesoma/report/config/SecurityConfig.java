package org.awesoma.report.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.awesoma.security.config.JwtConfiguration;
import org.awesoma.security.exception.IdentityServiceUnavailableException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Roles come from the verified token and decide what kind of operation is allowed at all.
 * Whether the user is active and may use the given project is asked of monitor-service for
 * each request, so a blocked user or a revoked membership takes effect at once.
 */
@Configuration(proxyBeanMethods = false)
@Import(JwtConfiguration.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, JwtAuthenticationConverter converter,
            ObjectMapper json) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .requestMatchers("/internal/**").hasRole("SERVICE")
                        .requestMatchers("/actuator/**").hasRole("SUPERVISOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole("SUPERVISOR", "OPERATOR", "VIEWER")
                        .requestMatchers("/api/v1/**").hasAnyRole("SUPERVISOR", "OPERATOR")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                problem(response, json, authenticationStatus(error)))
                        .accessDeniedHandler((request, response, error) ->
                                problem(response, json, HttpStatus.FORBIDDEN)))
                .oauth2ResourceServer(resource -> resource
                        .authenticationEntryPoint((request, response, error) ->
                                problem(response, json, authenticationStatus(error)))
                        .accessDeniedHandler((request, response, error) ->
                                problem(response, json, HttpStatus.FORBIDDEN))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }

    private HttpStatus authenticationStatus(AuthenticationException error) {
        return error instanceof IdentityServiceUnavailableException
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNAUTHORIZED;
    }

    private void problem(HttpServletResponse response, ObjectMapper json, HttpStatus status) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        if (status == HttpStatus.UNAUTHORIZED) response.setHeader("WWW-Authenticate", "Bearer");
        json.writeValue(response.getOutputStream(), Map.of("type", "about:blank", "title", status.getReasonPhrase(),
                "status", status.value(), "detail", status == HttpStatus.SERVICE_UNAVAILABLE
                        ? "User verification is unavailable" : status == HttpStatus.UNAUTHORIZED
                        ? "A valid Bearer token is required" : "Insufficient permissions"));
    }
}
