package org.awesoma.monitoring.web.exception;

/**
 * The request is well formed but collides with the current state — a duplicate unique
 * value, or an operation the resource's state does not allow. The handler turns this
 * into 409.
 */
public class ConflictStateException extends RuntimeException {

    public ConflictStateException(String message) {
        super(message);
    }
}
