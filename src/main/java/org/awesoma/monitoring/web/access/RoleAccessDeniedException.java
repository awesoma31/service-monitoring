package org.awesoma.monitoring.web.access;

public class RoleAccessDeniedException extends RuntimeException {

    public RoleAccessDeniedException(String message) {
        super(message);
    }
}
