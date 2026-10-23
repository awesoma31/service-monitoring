package org.awesoma.user.security;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserRepository users;
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
            long userId = Long.parseLong(jwt.getSubject());
            var user = users.findById(userId)
                    .filter(value -> !value.isDeleted() && value.getStatus() == UserStatus.ACTIVE)
                    .orElseThrow(() -> {
                        log.warn("JWT authentication rejected, user unavailable: user_id={}", userId);
                        return new BadCredentialsException("User is unavailable");
                    });
            return new JwtAuthenticationToken(jwt,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        } catch (NumberFormatException error) {
            log.warn("Invalid user identity in JWT: subject={}", jwt.getSubject(), error);
            throw new BadCredentialsException("Invalid user identity");
        }
    }
}
