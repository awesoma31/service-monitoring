package org.awesoma.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Service identity is independent of an incoming user and works from schedulers as well. */
@Configuration(proxyBeanMethods = false)
public class ServiceTokenConfiguration {
    @Bean
    @ConditionalOnMissingBean(ServiceTokenProvider.class)
    public ServiceTokenProvider serviceTokenProvider(ObjectMapper json,
            @Value("${security.service-auth.token-uri:http://localhost:8084/internal/auth/token}") String uri,
            @Value("${spring.application.name}") String serviceName,
            @Value("${security.service-auth.client-secret:}") String secret) {
        return new ClientCredentialsTokenProvider(json, uri, serviceName, secret);
    }

    @Bean
    public RequestInterceptor serviceAuthentication(ServiceTokenProvider tokens) {
        return request -> request.header("Authorization", "Bearer " + tokens.token());
    }
}
