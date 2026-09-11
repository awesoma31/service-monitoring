package org.awesoma.monitoring.web.dto.project;

import jakarta.validation.constraints.NotNull;
import org.awesoma.monitoring.domain.enums.MemberRole;

public record ProjectMemberRequest(@NotNull Long userId, @NotNull MemberRole role) {
}
