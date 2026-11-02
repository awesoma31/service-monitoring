package org.awesoma.report.application.port;

public class AccessUnavailableException extends RuntimeException {
    public AccessUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
