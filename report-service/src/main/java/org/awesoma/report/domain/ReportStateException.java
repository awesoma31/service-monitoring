package org.awesoma.report.domain;

/** A change the current state of the report does not allow. */
public class ReportStateException extends RuntimeException {
    public ReportStateException(String message) {
        super(message);
    }
}
