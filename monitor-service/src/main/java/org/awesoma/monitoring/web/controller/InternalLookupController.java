package org.awesoma.monitoring.web.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.service.IncidentService;
import org.awesoma.monitoring.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets notification-service check the projects and incidents it refers to by id. Like the
 * rest of /internal/**, not routed by the gateway and hidden from the API docs.
 */
@Hidden
@Validated
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalLookupController {

    private final ProjectService projectService;
    private final IncidentService incidentService;

    @GetMapping("/projects/{id}/exists")
    public ResponseEntity<Boolean> projectExists(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(projectService.exists(id));
    }

    @GetMapping("/incidents/{id}/exists")
    public ResponseEntity<Boolean> incidentExists(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(incidentService.exists(id));
    }
}
