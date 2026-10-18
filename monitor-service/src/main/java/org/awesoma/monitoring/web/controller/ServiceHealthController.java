package org.awesoma.monitoring.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.service.ServiceHealthService;
import org.awesoma.monitoring.web.dto.common.ServiceHealthResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/services", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Service health", description = "Downstream availability and Feign circuit breaker state")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Downstream service is healthy",
            content = @Content(schema = @Schema(implementation = ServiceHealthResponse.class))),
    @ApiResponse(responseCode = "503", description = "Downstream service is unavailable or its circuit is open",
            content = @Content(schema = @Schema(implementation = ServiceHealthResponse.class)))
})
public class ServiceHealthController {

    private final ServiceHealthService health;

    @GetMapping("/check-service/health")
    @Operation(summary = "Check check-service through its Feign circuit breaker",
            description = "Calls check-service /actuator/health. Returns the health status, fallback marker "
                    + "and circuit breaker state after the call. An open circuit skips the downstream request.")
    public ServiceHealthResponse checkService(HttpServletResponse response) {
        return respond(health.checkService(), response);
    }

    @GetMapping("/notification-service/health")
    @Operation(summary = "Check notification-service through its Feign circuit breaker",
            description = "Calls notification-service /actuator/health. Returns the health status, fallback marker "
                    + "and circuit breaker state after the call. An open circuit skips the downstream request.")
    public ServiceHealthResponse notificationService(HttpServletResponse response) {
        return respond(health.notificationService(), response);
    }

    private ServiceHealthResponse respond(ServiceHealthResponse health, HttpServletResponse response) {
        response.setStatus(health.available() ? HttpStatus.OK.value() : HttpStatus.SERVICE_UNAVAILABLE.value());
        return health;
    }
}
