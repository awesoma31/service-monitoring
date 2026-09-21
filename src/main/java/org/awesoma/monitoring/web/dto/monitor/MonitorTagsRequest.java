package org.awesoma.monitoring.web.dto.monitor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

/** Replaces the whole tag set; unknown tag names are created on the fly. */
public record MonitorTagsRequest(
        @Schema(example = "[\"production\", \"api\"]")
                @NotNull
                Set<@NotBlank @Size(max = 50) String> tags) {
}
