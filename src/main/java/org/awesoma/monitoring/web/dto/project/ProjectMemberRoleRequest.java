package org.awesoma.monitoring.web.dto.project;

import jakarta.validation.constraints.NotNull;
import org.awesoma.monitoring.domain.enums.MemberRole;

public record ProjectMemberRoleRequest(@NotNull MemberRole role) {
}
