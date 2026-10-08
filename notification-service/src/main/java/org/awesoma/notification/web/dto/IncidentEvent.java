package org.awesoma.notification.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.awesoma.notification.domain.IncidentKind;

/** Sent by monitor-service after an incident opens or closes; one alert per enabled channel. */
public record IncidentEvent(
        @NotNull @Positive Long incidentId,
        @NotNull @Positive Long projectId,
        @NotNull IncidentKind kind) {
}
