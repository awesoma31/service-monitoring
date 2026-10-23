package org.awesoma.monitoring.security;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.monitoring.service.UserDirectory;
import org.awesoma.security.exception.IdentityServiceUnavailableException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserDirectory users;
    private final JwtAuthenticationConverter serviceConverter;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var roles = jwt.getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty()) {
            log.warn("JWT authentication rejected, missing role: subject={}", jwt.getSubject());
            throw new BadCredentialsException("Missing role");
        }
        if (roles.contains("SERVICE")) {
            return serviceConverter.convert(jwt);
        }
        try {
            var user = users.security(Long.parseLong(jwt.getSubject()));
            if (!user.active()) {
                log.warn("JWT authentication rejected, user unavailable: user_id={}", user.id());
                throw new BadCredentialsException("User is unavailable");
            }
            // Blocking or changing a role takes effect immediately in monitor-service.
            return new JwtAuthenticationToken(jwt,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
        } catch (NumberFormatException error) {
            log.warn("Invalid user identity in JWT: subject={}", jwt.getSubject(), error);
            throw new BadCredentialsException("Invalid user identity");
        } catch (ResponseStatusException error) {
            throw new IdentityServiceUnavailableException(error);
        }
    }
}
