package org.awesoma.user.security;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.awesoma.user.domain.enums.UserStatus;
import org.awesoma.user.repository.UserRepository;
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
    private final UserRepository users;
    private final JwtAuthenticationConverter serviceConverter;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var roles = jwt.getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty()) throw new BadCredentialsException("Missing role");
        if (roles.contains("SERVICE")) {
            return serviceConverter.convert(jwt);
        }
        try {
            var user = users.findById(Long.parseLong(jwt.getSubject()))
                    .filter(value -> !value.isDeleted() && value.getStatus() == UserStatus.ACTIVE)
                    .orElseThrow(() -> new BadCredentialsException("User is unavailable"));
            // Blocking or changing a role takes effect immediately in monitor-service.
            return new JwtAuthenticationToken(jwt,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        } catch (NumberFormatException error) {
            throw new BadCredentialsException("Invalid user identity");
        }
    }
}
