package org.awesoma.monitoring.web.dto.monitor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Replaces the whole tag set; unknown tag names are created on the fly. */
public record MonitorTagsRequest(@NotNull Set<@NotBlank @Size(max = 50) String> tags) {
}
