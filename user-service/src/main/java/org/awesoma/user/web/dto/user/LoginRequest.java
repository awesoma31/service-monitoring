package org.awesoma.user.web.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 255)
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String password) {}
