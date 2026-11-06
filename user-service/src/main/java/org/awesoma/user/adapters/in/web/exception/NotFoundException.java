package org.awesoma.user.adapters.in.web.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException("%s %s not found".formatted(resource, id));
    }
}
