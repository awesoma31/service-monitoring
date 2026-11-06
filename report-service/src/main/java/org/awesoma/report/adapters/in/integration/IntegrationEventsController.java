package org.awesoma.report.adapters.in.integration;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.awesoma.integration.IntegrationEvent;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives resource events from monitor-service's outbox when the HTTP transport is used. A
 * failure answer makes the outbox send the same event again; the inbox makes that harmless.
 */
@Hidden
@RestController
@RequiredArgsConstructor
class IntegrationEventsController {
    private final ResourceEventReceiver receiver;

    @PostMapping("/internal/integration/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void handle(@RequestBody IntegrationEvent event) {
        receiver.receive(event);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    void invalidEvent() {
    }
}
