package org.awesoma.check.web;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.awesoma.check.repository.CheckResultRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Called by monitor-service when a monitor is deleted. Not routed by the gateway and hidden
 * from the API docs, like the rest of /internal/**.
 */
@Hidden
@RestController
@RequestMapping("/internal/results")
@RequiredArgsConstructor
public class InternalResultController {

    private final CheckResultRepository results;

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Flux<Void> deleteHistory(@RequestParam("monitor_id") @Positive Long monitorId) {
        return results.deleteByMonitorId(monitorId).then().flux();
    }
}
