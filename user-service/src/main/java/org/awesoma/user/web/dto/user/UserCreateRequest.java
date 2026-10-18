package org.awesoma.user.web.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.awesoma.user.domain.enums.UserRole;

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
                        maxLength = 255)
                @NotBlank
                @Size(min = 8, max = 255)
                @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
                String password,
        @Schema(example = "Alex Operator") @NotBlank @Size(max = 255) String fullName,
        @Schema(defaultValue = "VIEWER") UserRole role) {

    public UserCreateRequest {
        role = role == null ? UserRole.VIEWER : role;
    }

    public UserCreateRequest(String email, String password, String fullName) {
        this(email, password, fullName, UserRole.VIEWER);
    }
}
