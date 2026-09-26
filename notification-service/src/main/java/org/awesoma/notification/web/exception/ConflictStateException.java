package org.awesoma.notification.web.exception;

/** The request is well formed but collides with the current state; answered with 409. */
public class ConflictStateException extends RuntimeException {

    public ConflictStateException(String message) {
        super(message);
    }
}
