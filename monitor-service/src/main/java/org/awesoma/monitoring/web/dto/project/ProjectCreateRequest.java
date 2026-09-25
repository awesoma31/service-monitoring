package org.awesoma.monitoring.web.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record ProjectCreateRequest(
        @Schema(example = "1") @NotNull @Positive Long ownerId,
        @Schema(example = "Production services") @NotBlank @Size(max = 255) String name,
        @Schema(example = "production-services")
                @NotBlank
                @Size(max = 100)
                @Pattern(
                        regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                        message = "must be lowercase words separated by single hyphens")
                String slug) {
}
