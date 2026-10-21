package org.awesoma.monitoring.web.controller;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.security.AccessPolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequiredArgsConstructor
public class InternalAccessController {
    private final AccessPolicy access;

    @GetMapping("/internal/access/{resource}/{id}")
    public boolean allowed(@PathVariable String resource, @PathVariable @Positive Long id,
            @RequestParam("user_id") @Positive Long userId, @RequestParam boolean write) {
        return access.allowed(resource, id, userId, write);
    }
}
