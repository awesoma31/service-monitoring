package org.awesoma.monitoring.web.dto.project;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import org.awesoma.monitoring.domain.enums.MemberRole;

public record ProjectMemberRoleRequest(@Schema(example = "VIEWER") @NotNull MemberRole role) {
}
