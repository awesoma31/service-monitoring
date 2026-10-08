package org.awesoma.monitoring.domain.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.awesoma.monitoring.domain.enums.CheckResultType;
import org.awesoma.monitoring.domain.enums.Severity;

public record ProbeOutcome(
        @NotNull CheckResultType result,
        @PositiveOrZero Integer responseMs,
        @Min(100) @Max(599) Integer httpStatus,
        @Size(max = 500) String errorMessage,
        @NotNull UUID claimToken) {

    public static ProbeOutcome success(int responseMs, int httpStatus) {
        return new ProbeOutcome(CheckResultType.SUCCESS, responseMs, httpStatus, null, null);
    }

    public static ProbeOutcome badStatus(int responseMs, int httpStatus, int expectedStatus) {
        return new ProbeOutcome(
                CheckResultType.BAD_STATUS,
                responseMs,
                httpStatus,
                "Expected status %d but got %d".formatted(expectedStatus, httpStatus),
                null);
    }

    public static ProbeOutcome timeout(int responseMs, String message) {
        return new ProbeOutcome(CheckResultType.TIMEOUT, responseMs, null, message, null);
    }

    public static ProbeOutcome connectionError(int responseMs, String message) {
        return new ProbeOutcome(CheckResultType.CONNECTION_ERROR, responseMs, null, message, null);
    }

    public ProbeOutcome forClaim(UUID token) {
        return new ProbeOutcome(result, responseMs, httpStatus, errorMessage, token);
    }

    public boolean isFailure() {
        return result.isFailure();
    }

    /**
     * An unreachable host is worse than an unexpected status code: the first means nobody
     * can use the service at all, the second that it answers but wrongly.
     */
    public Severity severity() {
        return switch (result) {
            case CONNECTION_ERROR, TIMEOUT -> Severity.HIGH;
            case BAD_STATUS -> Severity.MEDIUM;
            case SUCCESS -> Severity.LOW;
        };
    }
}
