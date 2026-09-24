package org.awesoma.monitoring.web.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record ProjectUpdateRequest(
        @Schema(example = "Production platform") @NotBlank @Size(max = 255) String name) {
}
