package org.awesoma.monitoring.web.dto.project;

import java.time.OffsetDateTime;

public record ProjectMemberResponse(
        Long userId, String email, String fullName, OffsetDateTime joinedAt) {
}
