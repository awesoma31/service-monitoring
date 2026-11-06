package org.awesoma.user.adapters.in.web.dto.user;

import org.awesoma.user.domain.enums.UserRole;
import org.awesoma.user.domain.enums.UserStatus;

public record UserResponse(Long id, String email, String fullName, UserStatus status, UserRole role) {
}
