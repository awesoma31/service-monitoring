package org.awesoma.report.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /** Documents fields under the same snake_case names the API reads and writes. */
    @Bean
    public ModelResolver modelResolver(ObjectMapper objectMapper) {
        return new ModelResolver(objectMapper);
    }

    @Bean
    public OpenAPI reportOpenApi() {
        var problem = new Content().addMediaType("application/problem+json",
                new MediaType().schema(new Schema<>().type("object")));
        return new OpenAPI()
                .servers(List.of(new Server().url("/")))
                .info(new Info()
                        .title("Report API")
                        .version("v1")
                        .description("Stored and generated CSV and PDF reports of monitoring projects."))
                .components(new Components().addResponses("BadRequest",
                        new ApiResponse().description("The request or the file is invalid").content(problem)))
                .tags(List.of(new Tag().name("Reports").description("Stored report files of a project")));
    }
}
