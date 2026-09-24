package org.awesoma.monitoring.web.dto.channel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import org.awesoma.monitoring.domain.enums.ChannelType;

public record ChannelCreateRequest(
        @Schema(example = "EMAIL") @NotNull ChannelType type,
        @Schema(example = "oncall@example.com") @NotBlank @Size(max = 512) String target) {
}
