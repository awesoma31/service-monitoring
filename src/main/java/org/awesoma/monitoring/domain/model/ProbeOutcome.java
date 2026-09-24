package org.awesoma.monitoring.domain.model;

import org.awesoma.monitoring.domain.enums.CheckResultType;
import org.awesoma.monitoring.domain.enums.Severity;

public record ProbeOutcome(
        CheckResultType result, Integer responseMs, Integer httpStatus, String errorMessage) {

    public static ProbeOutcome success(int responseMs, int httpStatus) {
        return new ProbeOutcome(CheckResultType.SUCCESS, responseMs, httpStatus, null);
    }

    public static ProbeOutcome badStatus(int responseMs, int httpStatus, int expectedStatus) {
        return new ProbeOutcome(
                CheckResultType.BAD_STATUS,
                responseMs,
                httpStatus,
                "Expected status %d but got %d".formatted(expectedStatus, httpStatus));
    }

    public static ProbeOutcome timeout(int responseMs, String message) {
        return new ProbeOutcome(CheckResultType.TIMEOUT, responseMs, null, message);
    }

    public static ProbeOutcome connectionError(int responseMs, String message) {
        return new ProbeOutcome(CheckResultType.CONNECTION_ERROR, responseMs, null, message);
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
