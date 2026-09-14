package org.awesoma.monitoring.web.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The slug is part of a project's identity and stays fixed once created. */
public record ProjectUpdateRequest(@NotBlank @Size(max = 255) String name) {
}
