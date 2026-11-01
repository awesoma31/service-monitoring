package org.awesoma.check.integration;

import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Hidden;
import org.awesoma.integration.IntegrationEvent;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import reactor.core.publisher.Mono;

@RestController
@Hidden
@RequestMapping("/internal/integration")
@RequiredArgsConstructor
class CheckEventsController {
    private final CheckEventHandler events;
    @PostMapping("/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> handle(@RequestBody IntegrationEvent event) { return events.handle(event); }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    void invalidEvent() {}
}
