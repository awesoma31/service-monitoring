package org.awesoma.check.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Schemas follow the application's snake_case naming, as in monitor-service. */
    @Bean
    public ModelResolver modelResolver(ObjectMapper objectMapper) {
        return new ModelResolver(objectMapper);
    }

    @Bean
    public OpenAPI checkServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Check Service API")
                .description("History of the HTTP probes the service performs on monitors")
                .version("v1"));
    }
}
