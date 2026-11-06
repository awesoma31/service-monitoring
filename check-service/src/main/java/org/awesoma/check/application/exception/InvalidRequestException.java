package org.awesoma.check.application.exception;

/** A request that is malformed as a whole, such as a cursor made for another query. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
