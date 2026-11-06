package org.awesoma.user.adapters.in.web.dto.user;

import jakarta.validation.constraints.NotNull;
import org.awesoma.user.domain.enums.UserRole;

public record UserRoleUpdateRequest(@NotNull UserRole role) {}
