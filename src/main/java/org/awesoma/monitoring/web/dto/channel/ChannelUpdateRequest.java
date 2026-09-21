package org.awesoma.monitoring.web.dto.channel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/** The type and project of a channel are fixed; only its destination and state change. */
public record ChannelUpdateRequest(
        @Schema(example = "backup-oncall@example.com") @NotBlank @Size(max = 512) String target,
        @Schema(example = "true") boolean enabled) {
}
