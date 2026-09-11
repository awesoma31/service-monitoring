package org.awesoma.monitoring.web.dto.project;

import java.time.OffsetDateTime;
import org.awesoma.monitoring.domain.enums.MemberRole;

public record ProjectMemberResponse(
        Long userId, String email, String fullName, MemberRole role, OffsetDateTime joinedAt) {
}
