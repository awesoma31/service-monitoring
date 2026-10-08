package org.awesoma.monitoring.web.dto.project;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record OwnerNotificationsUpdateRequest(
        @Schema(
                        description = "Whether incident notifications are enabled for the project owner",
                        example = "false")
                @NotNull
                Boolean enabled) {
}
