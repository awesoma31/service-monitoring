package org.awesoma.monitoring.web.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import org.awesoma.monitoring.domain.enums.UserStatus;

public record UserUpdateRequest(
        @Schema(example = "Alex Operator") @NotBlank @Size(max = 255) String fullName,
        @Schema(example = "ACTIVE") @NotNull UserStatus status) {
}
