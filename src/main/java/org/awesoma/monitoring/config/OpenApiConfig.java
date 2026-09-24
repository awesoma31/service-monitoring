package org.awesoma.monitoring.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.List;
import java.util.Map;
import org.awesoma.monitoring.web.dto.common.ApiErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI serviceMonitoringOpenApi() {
        Components components = new Components();
        ModelConverters.getInstance()
                .readAll(ApiErrorResponse.class)
                .forEach(components::addSchemas);
        components
                .addResponses(
                        "BadRequest",
                        problemResponse(
                                "The request is malformed or violates validation rules",
                                Map.of(
                                        "type", "about:blank",
                                        "title", "Request validation failed",
                                        "status", 400,
                                        "detail", "One or more request values are invalid",
                                        "instance", "/api/v1/users",
                                        "violations", List.of(Map.of(
                                                "field", "email",
                                                "message", "must be a well-formed email address")))))
                .addResponses(
                        "NotFound",
                        problemResponse(
                                "The addressed resource does not exist",
                                Map.of(
                                        "type", "about:blank",
                                        "title", "Resource not found",
                                        "status", 404,
                                        "detail", "Monitor 42 not found",
                                        "instance", "/api/v1/monitors/42")))
                .addResponses(
                        "Conflict",
                        problemResponse(
                                "The request conflicts with the current resource or database state",
                                Map.of(
                                        "type", "about:blank",
                                        "title", "Conflicting state",
                                        "status", 409,
                                        "detail", "Slug production is already taken",
                                        "instance", "/api/v1/projects")));

        return new OpenAPI()
                .info(new Info()
                        .title("Service Monitoring API")
                        .version("v1")
                        .description("REST API for projects that periodically check HTTP endpoints, "
                                + "record check history, open incidents and prepare notifications."))
                .components(components)
                .tags(List.of(
                        new io.swagger.v3.oas.models.tags.Tag()
                                .name("Users")
                                .description("User lifecycle management"),
                        new io.swagger.v3.oas.models.tags.Tag()
                                .name("Projects")
                                .description("Projects and their members"),
                        new io.swagger.v3.oas.models.tags.Tag()
                                .name("Monitors")
                                .description("HTTP monitoring targets and their tags"),
                        new io.swagger.v3.oas.models.tags.Tag()
                                .name("Tags")
                                .description("Reusable monitor labels"),
                        new io.swagger.v3.oas.models.tags.Tag()
                                .name("Incidents")
                                .description("Check history, incidents and generated notifications"),
                        new io.swagger.v3.oas.models.tags.Tag()
                                .name("Channels")
                                .description("Project notification destinations")));
    }

    private ApiResponse problemResponse(String description, Map<String, Object> exampleValue) {
        Schema<?> schema = new Schema<>().$ref("#/components/schemas/ApiError");
        MediaType mediaType = new MediaType()
                .schema(schema)
                .addExamples("problem", new Example().value(exampleValue));
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/problem+json", mediaType));
    }
}
