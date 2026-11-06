package org.awesoma.user.application.usecase;

import java.util.UUID;
import org.awesoma.user.application.exception.InvalidCredentials;
import org.awesoma.user.application.model.Token;
import org.awesoma.user.application.port.ClientStore;
import org.awesoma.user.application.port.PasswordHasher;
import org.awesoma.user.application.port.TokenIssuer;
import org.awesoma.user.application.port.Transactions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Authenticates a configured service with its own credentials. */
public class ServiceAuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(ServiceAuthenticationService.class);

    private final ClientStore clients;
    private final PasswordHasher passwords;
    private final TokenIssuer tokens;
    private final Transactions transactions;
    private final String dummyHash;

    public ServiceAuthenticationService(ClientStore clients, PasswordHasher passwords, TokenIssuer tokens,
            Transactions transactions) {
        this.clients = clients;
        this.passwords = passwords;
        this.tokens = tokens;
        this.transactions = transactions;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public Token authenticate(String id, String secret) {
        return transactions.read(() -> {
            var client = clients.findById(id);
            boolean matches = passwords.matches(secret, client.map(value -> value.secretHash()).orElse(dummyHash));
            if (client.isEmpty() || !matches || !client.get().enabled()) {
                log.warn("Service authentication rejected: client_id={}", id);
                throw new InvalidCredentials();
            }
            var token = tokens.issue(id, "SERVICE");
            log.debug("Service authentication succeeded: client_id={}", id);
            return token;
        });
    }
}
