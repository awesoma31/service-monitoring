package org.awesoma.report.application.port;

public class SourceUnavailableException extends RuntimeException {
    public SourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
