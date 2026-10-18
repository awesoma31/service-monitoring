package org.awesoma.monitoring.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.awesoma.security.JwtConfiguration;
import org.awesoma.security.PasswordHashing;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@Import({JwtConfiguration.class, DatabaseJwtAuthenticationConverter.class})
public class SecurityConfig {
    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, DatabaseJwtAuthenticationConverter converter,
            ObjectMapper json) throws Exception {
        return http.csrf(csrf -> csrf.disable()).httpBasic(basic -> basic.disable()).formLogin(form -> form.disable())
                .requestCache(cache -> cache.requestCache(new org.springframework.security.web.savedrequest.NullRequestCache()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/internal/**").hasRole("SERVICE")
                        .requestMatchers("/api/v1/services/**", "/actuator/**").hasRole("SUPERVISOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole("SUPERVISOR", "OPERATOR", "VIEWER")
                        .requestMatchers("/api/v1/**").hasAnyRole("SUPERVISOR", "OPERATOR")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> problem(response, json, authenticationStatus(error)))
                        .accessDeniedHandler((request, response, error) -> problem(response, json, HttpStatus.FORBIDDEN)))
                .oauth2ResourceServer(resource -> resource
                        .authenticationEntryPoint((request, response, error) -> problem(response, json, authenticationStatus(error)))
                        .accessDeniedHandler((request, response, error) -> problem(response, json, HttpStatus.FORBIDDEN))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }

    private HttpStatus authenticationStatus(org.springframework.security.core.AuthenticationException error) {
        return error instanceof org.awesoma.security.IdentityServiceUnavailableException
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNAUTHORIZED;
    }

    private void problem(HttpServletResponse response, ObjectMapper json, HttpStatus status) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        if (status == HttpStatus.UNAUTHORIZED) response.setHeader("WWW-Authenticate", "Bearer");
        json.writeValue(response.getOutputStream(), Map.of("type", "about:blank", "title", status.getReasonPhrase(),
                "status", status.value(), "detail", status == HttpStatus.SERVICE_UNAVAILABLE
                        ? "User verification is unavailable" : status == HttpStatus.UNAUTHORIZED
                        ? "A valid Bearer token is required" : "Insufficient permissions"));
    }
}
