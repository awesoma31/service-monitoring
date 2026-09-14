package org.awesoma.monitoring.web.dto.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagCreateRequest(@NotBlank @Size(max = 50) String name) {
}
