package org.awesoma.user.web.dto.user;

import org.awesoma.user.domain.enums.UserStatus;
import org.awesoma.user.domain.enums.UserRole;

public record UserResponse(Long id, String email, String fullName, UserStatus status, UserRole role) {
}
