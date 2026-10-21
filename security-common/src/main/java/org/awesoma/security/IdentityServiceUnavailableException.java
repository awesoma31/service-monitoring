package org.awesoma.security;

import org.springframework.security.core.AuthenticationException;

public class IdentityServiceUnavailableException extends AuthenticationException {
    public IdentityServiceUnavailableException(Throwable cause) {
        super("Identity service is unavailable", cause);
    }
}
