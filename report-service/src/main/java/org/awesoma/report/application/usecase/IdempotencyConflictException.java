package org.awesoma.report.application.usecase;

/** The Idempotency-Key is bound to other parameters, or to a report that was deleted. */
public class IdempotencyConflictException extends RuntimeException {
    private final String code;

    public IdempotencyConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static IdempotencyConflictException otherParameters() {
        return new IdempotencyConflictException("IDEMPOTENCY_KEY_REUSED",
                "Idempotency-Key was already used with other parameters");
    }

    public static IdempotencyConflictException reportDeleted() {
        return new IdempotencyConflictException("REPORT_DELETED",
                "Idempotency-Key belongs to a report that was deleted");
    }

    public String code() {
        return code;
    }
}
