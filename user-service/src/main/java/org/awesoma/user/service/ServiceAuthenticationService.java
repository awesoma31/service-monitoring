package org.awesoma.user.service;

import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.security.jwt.JwtTokens;
import org.awesoma.user.domain.entity.ServiceClient;
import org.awesoma.user.repository.ServiceClientRepository;
import org.awesoma.user.web.dto.user.TokenResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional(readOnly = true)
public class ServiceAuthenticationService {
    private final ServiceClientRepository clients;
    private final PasswordEncoder passwords;
    private final JwtTokens tokens;
    private final String dummyHash;

    public ServiceAuthenticationService(ServiceClientRepository clients, PasswordEncoder passwords, JwtTokens tokens) {
        this.clients = clients;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public TokenResponse authenticate(String id, String secret) {
        var client = clients.findById(id);
        boolean matches = passwords.matches(secret, client.map(ServiceClient::getSecretHash).orElse(dummyHash));
        if (client.isEmpty() || !matches || !client.get().isEnabled()) {
            log.warn("Service authentication rejected: client_id={}", id);
            throw new BadCredentialsException("Invalid service credentials");
        }
        var token = tokens.issue(id, "SERVICE");
        log.debug("Service authentication succeeded: client_id={}", id);
        return new TokenResponse(token.getTokenValue(), "Bearer",
                Duration.between(token.getIssuedAt(), token.getExpiresAt()).toSeconds());
    }
}
