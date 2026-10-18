package org.awesoma.monitoring.security;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.service.UserDirectory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserDirectory users;
    private final JwtAuthenticationConverter serviceConverter;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var roles = jwt.getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty()) throw new BadCredentialsException("Missing role");
        if (roles.contains("SERVICE")) {
            return serviceConverter.convert(jwt);
        }
        try {
            var user = users.security(Long.parseLong(jwt.getSubject()));
            if (!user.active()) throw new BadCredentialsException("User is unavailable");
            // Blocking or changing a role takes effect immediately in monitor-service.
            return new JwtAuthenticationToken(jwt,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
        } catch (NumberFormatException error) {
            throw new BadCredentialsException("Invalid user identity");
        } catch (org.springframework.web.server.ResponseStatusException error) {
            throw new org.awesoma.security.IdentityServiceUnavailableException(error);
        }
    }
}
