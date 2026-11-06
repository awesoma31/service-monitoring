package org.awesoma.report.adapters.in.web;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

/** The author always comes from the verified token, never from a field of the request. */
final class CurrentUser {

    private CurrentUser() {
    }

    static long id(Jwt jwt) {
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException error) {
            throw new BadCredentialsException("Token does not identify a user");
        }
    }
}
