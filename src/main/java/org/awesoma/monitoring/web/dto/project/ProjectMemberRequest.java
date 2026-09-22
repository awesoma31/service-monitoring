package org.awesoma.monitoring.web.dto.project;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;
import org.awesoma.monitoring.domain.enums.MemberRole;

public record ProjectMemberRequest(
        @Schema(example = "2") @NotNull @Positive Long userId,
        @Schema(example = "EDITOR") @NotNull MemberRole role) {
}
