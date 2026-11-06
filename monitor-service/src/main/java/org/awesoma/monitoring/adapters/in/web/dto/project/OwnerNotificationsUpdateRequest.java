package org.awesoma.monitoring.adapters.in.web.dto.project;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record OwnerNotificationsUpdateRequest(
        @Schema(
                        description = "Whether incident notifications are enabled for the project owner",
                        example = "false")
                @NotNull
                Boolean enabled)
        implements org.awesoma.monitoring.application.port.command.OwnerNotificationsUpdateRequest {
}
