package org.awesoma.notification.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Sent by monitor-service after an incident opens or closes; one alert per enabled channel. */
public record IncidentEvent(
        @NotNull @Positive Long incidentId, @NotNull @Positive Long projectId, @NotNull Kind kind) {

    public enum Kind {
        OPENED,
        RESOLVED
    }
}
