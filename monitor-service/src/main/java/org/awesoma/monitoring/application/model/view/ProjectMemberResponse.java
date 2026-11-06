package org.awesoma.monitoring.application.model.view;

import java.time.OffsetDateTime;

public record ProjectMemberResponse(
        Long userId, String email, String fullName, OffsetDateTime joinedAt) {
}
