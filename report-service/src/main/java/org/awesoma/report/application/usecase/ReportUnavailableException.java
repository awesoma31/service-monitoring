package org.awesoma.report.application.usecase;

/** The operation could not be completed now; the client may try again later. */
public class ReportUnavailableException extends RuntimeException {
    public ReportUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
