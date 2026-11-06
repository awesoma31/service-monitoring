package org.awesoma.monitoring.adapters.in.web.dto.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record TagCreateRequest(
        @Schema(example = "production") @NotBlank @Size(max = 50) String name)
        implements org.awesoma.monitoring.application.port.command.TagCreateRequest {
}
