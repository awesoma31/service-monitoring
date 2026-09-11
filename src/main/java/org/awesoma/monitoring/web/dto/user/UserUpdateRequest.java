package org.awesoma.monitoring.web.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.awesoma.monitoring.domain.enums.UserStatus;

public record UserUpdateRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotNull UserStatus status) {
}
