package org.awesoma.monitoring.web.access;

public class MissingRoleException extends RuntimeException {

    public MissingRoleException(String message) {
        super(message);
    }
}
