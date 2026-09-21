package org.awesoma.monitoring.web.dto.project;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.awesoma.monitoring.domain.enums.MemberRole;

public record ProjectMemberRequest(@NotNull @Positive Long userId, @NotNull MemberRole role) {
}
