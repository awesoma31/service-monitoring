package org.awesoma.notification.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.awesoma.notification.service.NotificationService;
import org.awesoma.notification.web.dto.NotificationResponse;
import org.awesoma.notification.web.dto.PageParams;
import org.awesoma.security.RemoteAccess;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final RemoteAccess access;

    @GetMapping(
            value = "/incidents/{incidentId}/notifications",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_NDJSON_VALUE})
    @Operation(summary = "List the notifications of an incident")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notification page returned"),
        @ApiResponse(responseCode = "400", description = "Invalid paging parameters"),
        @ApiResponse(responseCode = "404", description = "Incident not found")
    })
    public Flux<NotificationResponse> listByIncident(
            @PathVariable @Positive Long incidentId, @Valid PageParams page,
            ServerHttpResponse response, @AuthenticationPrincipal Jwt jwt) {
        return access.require("incident", incidentId, jwt, false)
                .then(notificationService.listByIncident(incidentId, page.toPageable()))
                .doOnNext(result -> {
                    response.getHeaders().set("X-Total-Count", Long.toString(result.getTotalElements()));
                    response.getHeaders().set("X-Has-Next", Boolean.toString(result.hasNext()));
                })
                .flatMapIterable(result -> result.getContent());
    }
}
