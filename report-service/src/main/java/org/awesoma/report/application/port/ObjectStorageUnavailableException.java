package org.awesoma.report.application.port;

public class ObjectStorageUnavailableException extends RuntimeException {
    public ObjectStorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
