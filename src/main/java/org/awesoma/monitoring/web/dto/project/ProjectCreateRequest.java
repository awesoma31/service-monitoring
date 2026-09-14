package org.awesoma.monitoring.web.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProjectCreateRequest(
        @NotNull Long ownerId,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100)
                @Pattern(
                        regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                        message = "must be lowercase words separated by single hyphens")
                String slug) {
}
