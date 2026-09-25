package org.awesoma.monitoring.web.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.domain.model.MonitorTarget;
import org.awesoma.monitoring.domain.model.ProbeOutcome;
import org.awesoma.monitoring.service.MonitorCheckService;
import org.awesoma.monitoring.service.MonitorService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for other services, not for API clients: the gateway routes only /api/v1/**, so
 * these are reachable only inside the service network, and they stay out of the API docs.
 */
@Hidden
@Validated
@RestController
@RequestMapping("/internal/monitors")
@RequiredArgsConstructor
public class InternalMonitorController {

    private final MonitorCheckService monitorCheckService;
    private final MonitorService monitorService;

    @GetMapping("/due")
    public ResponseEntity<List<MonitorTarget>> due(
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return ResponseEntity.ok(monitorCheckService.findDueTargets(limit));
    }

    @PostMapping("/{id}/outcomes")
    public ResponseEntity<Void> recordOutcome(
            @PathVariable @Positive Long id, @RequestBody ProbeOutcome outcome) {
        monitorCheckService.record(id, outcome);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/exists")
    public ResponseEntity<Boolean> exists(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(monitorService.exists(id));
    }
}
