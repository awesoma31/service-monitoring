package org.awesoma.monitoring.web.dto.user;

import org.awesoma.monitoring.domain.enums.UserStatus;

public record UserResponse(Long id, String email, String fullName, UserStatus status) {
}
