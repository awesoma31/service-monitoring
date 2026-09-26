package org.awesoma.notification.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.awesoma.notification.domain.ChannelType;

public record ChannelCreateRequest(
        @Schema(example = "EMAIL") @NotNull ChannelType type,
        @Schema(example = "oncall@example.com") @NotBlank @Size(max = 512) String target) {
}
