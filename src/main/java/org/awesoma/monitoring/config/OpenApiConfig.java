package org.awesoma.monitoring.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.awesoma.monitoring.domain.enums.MemberRole;
import org.awesoma.monitoring.web.access.AllowedRoles;
import org.awesoma.monitoring.web.access.RoleAuthorizationInterceptor;
import org.awesoma.monitoring.web.dto.common.ApiErrorResponse;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
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
                                        "instance", "/api/v1/projects")))
                .addResponses(
                        "Unauthorized",
                        problemResponse(
                                "The X-User-Role header is missing or invalid",
                                Map.of(
                                        "type", "about:blank",
                                        "title", "Authentication required",
                                        "status", 401,
                                        "detail", "Header X-User-Role is required",
                                        "instance", "/api/v1/projects")))
                .addResponses(
                        "Forbidden",
                        problemResponse(
                                "The supplied role cannot perform this operation",
                                Map.of(
                                        "type", "about:blank",
                                        "title", "Access denied",
                                        "status", 403,
                                        "detail", "Role VIEWER cannot call DELETE /api/v1/monitors/1",
                                        "instance", "/api/v1/monitors/1")));

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

    @Bean
    public OperationCustomizer roleHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            AllowedRoles allowedRoles = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getMethod(), AllowedRoles.class);
            if (allowedRoles == null) {
                allowedRoles = AnnotatedElementUtils.findMergedAnnotation(
                        handlerMethod.getBeanType(), AllowedRoles.class);
            }
            if (allowedRoles == null) {
                return operation;
            }
            List<String> allowedRoleNames = Arrays.stream(allowedRoles.value())
                    .map(Enum::name)
                    .toList();
            List<String> allRoleNames = Arrays.stream(MemberRole.values())
                    .map(Enum::name)
                    .toList();
            operation.addParametersItem(new Parameter()
                    .in("header")
                    .name(RoleAuthorizationInterceptor.ROLE_HEADER)
                    .required(true)
                    .description("Caller role. Allowed values for this operation: "
                            + String.join(", ", allowedRoleNames))
                    .schema(new StringSchema()._enum(allRoleNames).example(allRoleNames.getFirst())));
            operation.getResponses()
                    .addApiResponse(
                            "401", new ApiResponse().$ref("#/components/responses/Unauthorized"));
            operation.getResponses()
                    .addApiResponse(
                            "403", new ApiResponse().$ref("#/components/responses/Forbidden"));
            return operation;
        };
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
