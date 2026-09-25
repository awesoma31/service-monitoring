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
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/incidents/{incidentId}/notifications")
    @Operation(summary = "List the notifications of an incident")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notification page returned"),
        @ApiResponse(responseCode = "400", description = "Invalid paging parameters"),
        @ApiResponse(responseCode = "404", description = "Incident not found")
    })
    public Mono<ResponseEntity<Page<NotificationResponse>>> listByIncident(
            @PathVariable @Positive Long incidentId, @Valid PageParams page) {
        return notificationService.listByIncident(incidentId, page.toPageable()).map(ResponseEntity::ok);
    }
}
