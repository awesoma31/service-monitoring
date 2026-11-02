package org.awesoma.report.domain;

/**
 * The caller acts for an attempt that is no longer current: its lease expired or was revoked,
 * or a newer attempt took over. Such a caller must not publish anything and only cleans up.
 */
public class StaleAttemptException extends ReportStateException {
    public StaleAttemptException(String message) {
        super(message);
    }
}
