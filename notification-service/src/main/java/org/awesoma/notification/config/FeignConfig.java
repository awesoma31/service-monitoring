package org.awesoma.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 * Feign encodes and decodes through Spring MVC message converters, which a WebFlux
 * application does not configure. They are provided here, backed by the application's
 * ObjectMapper so that request and response bodies use the same snake_case names.
 */
@Configuration
public class FeignConfig {

    @Bean
    public HttpMessageConverters feignMessageConverters(ObjectMapper objectMapper) {
        return new HttpMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper));
    }
}
