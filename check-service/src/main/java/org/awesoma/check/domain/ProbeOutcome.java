package org.awesoma.check.domain;

import java.util.UUID;

/**
 * Result of one probe, stored in the check history and reported to monitor-service, which
 * decides from it whether to open or close an incident.
 */
public record ProbeOutcome(
        CheckResultType result,
        Integer responseMs,
        Integer httpStatus,
        String errorMessage,
        UUID claimToken) {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

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
        return new ProbeOutcome(
                CheckResultType.TIMEOUT, responseMs, null, limitErrorMessage(message), null);
    }

    public static ProbeOutcome connectionError(int responseMs, String message) {
        return new ProbeOutcome(
                CheckResultType.CONNECTION_ERROR,
                responseMs,
                null,
                limitErrorMessage(message),
                null);
    }

    public ProbeOutcome forClaim(UUID token) {
        return new ProbeOutcome(result, responseMs, httpStatus, errorMessage, token);
    }

    private static String limitErrorMessage(String message) {
        return message == null || message.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
