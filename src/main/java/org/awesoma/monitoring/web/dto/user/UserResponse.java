package org.awesoma.monitoring.web.dto.user;

import org.awesoma.monitoring.domain.enums.UserStatus;

/** Deliberately carries no password field of any kind. */
public record UserResponse(Long id, String email, String fullName, UserStatus status) {
}
