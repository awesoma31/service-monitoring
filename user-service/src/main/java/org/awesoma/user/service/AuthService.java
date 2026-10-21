package org.awesoma.user.service;

import java.time.Duration;
import java.util.UUID;
import org.awesoma.security.JwtTokens;
import org.awesoma.user.domain.enums.UserStatus;
import org.awesoma.user.repository.UserRepository;
import org.awesoma.user.web.dto.user.LoginRequest;
import org.awesoma.user.web.dto.user.TokenResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtTokens tokens;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtTokens tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public TokenResponse login(LoginRequest request) {
        var user = users.findByEmail(request.email());
        boolean matches = passwords.matches(
                request.password(), user.map(value -> value.getPassword()).orElse(dummyHash));
        if (user.isEmpty() || !matches || user.get().isDeleted() || user.get().getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("Invalid email or password");
        }
        var jwt = tokens.issue(user.get().getId().toString(), user.get().getRole().name());
        return new TokenResponse(jwt.getTokenValue(), "Bearer",
                Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds());
    }
}
