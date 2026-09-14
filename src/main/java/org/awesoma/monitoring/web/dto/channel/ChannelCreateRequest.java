package org.awesoma.monitoring.web.dto.channel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.awesoma.monitoring.domain.enums.ChannelType;

public record ChannelCreateRequest(
        @NotNull ChannelType type, @NotBlank @Size(max = 512) String target) {
}
