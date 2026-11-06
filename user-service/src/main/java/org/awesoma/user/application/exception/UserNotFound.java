package org.awesoma.user.application.exception;
public class UserNotFound extends RuntimeException {
    public UserNotFound(long id) { super("User " + id + " not found"); }
}
