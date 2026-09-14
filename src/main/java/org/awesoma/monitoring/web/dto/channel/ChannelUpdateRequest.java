package org.awesoma.monitoring.web.dto.channel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The type and project of a channel are fixed; only its destination and state change. */
public record ChannelUpdateRequest(@NotBlank @Size(max = 512) String target, boolean enabled) {
}
