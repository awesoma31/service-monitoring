package org.awesoma.monitoring.web.exception;

/** The addressed resource does not exist; the handler turns this into 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException("%s %s not found".formatted(resource, id));
    }
}
