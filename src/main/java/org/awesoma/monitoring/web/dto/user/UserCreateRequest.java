package org.awesoma.monitoring.web.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserCreateRequest(
        @Schema(example = "operator@example.com")
                @NotBlank
                @Email
                @Size(max = 255)
                String email,
        @Schema(
                        example = "correct-horse-battery-staple",
                        format = "password",
                        accessMode = Schema.AccessMode.WRITE_ONLY,
                        minLength = 8,
                        maxLength = 72)
                @NotBlank
                @Size(min = 8, max = 72)
                String password,
        @Schema(example = "Alex Operator") @NotBlank @Size(max = 255) String fullName) {
}
