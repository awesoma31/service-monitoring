package org.awesoma.monitoring.adapters.in.web.dto.project;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;

public record ProjectMemberRequest(
        @Schema(example = "2") @NotNull @Positive Long userId)
        implements org.awesoma.monitoring.application.port.command.ProjectMemberRequest {
}
