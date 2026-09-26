package org.awesoma.check.domain;

/**
 * Result of one probe, stored in the check history and reported to monitor-service, which
 * decides from it whether to open or close an incident.
 */
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
}
