package org.awesoma.report.application.port;

import org.awesoma.report.domain.FailureCode;

/** The project is gone or a monitor no longer belongs to it: another attempt will not help. */
public class SourceRejectedException extends RuntimeException {
    private final FailureCode reason;

    public SourceRejectedException(FailureCode reason, String message) {
        super(message);
        this.reason = reason;
    }

    public FailureCode reason() {
        return reason;
    }
}
