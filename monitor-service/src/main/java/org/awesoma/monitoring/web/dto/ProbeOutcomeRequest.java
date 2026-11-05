package org.awesoma.monitoring.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.awesoma.monitoring.domain.enums.CheckResultType;
import org.awesoma.monitoring.domain.model.ProbeOutcome;

public record ProbeOutcomeRequest(@NotNull CheckResultType result,
        @PositiveOrZero Integer responseMs, @Min(100) @Max(599) Integer httpStatus,
        @Size(max = 500) String errorMessage, @NotNull UUID claimToken) {
    public ProbeOutcome toDomain() {
        return new ProbeOutcome(result,responseMs,httpStatus,errorMessage,claimToken);
    }
}
