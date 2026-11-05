package org.awesoma.user.application.usecase;

import java.util.Map;
import org.awesoma.user.application.port.ClientStore;
import org.awesoma.user.application.port.PasswordHasher;
import org.awesoma.user.application.port.Transactions;
import org.awesoma.user.domain.ServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Synchronizes configured service credentials without resetting existing client status. */
public class ClientCredentialsSetup {
    private static final Logger log = LoggerFactory.getLogger(ClientCredentialsSetup.class);
    private final ClientStore clients;
    private final PasswordHasher passwords;
    private final Transactions transactions;

    public ClientCredentialsSetup(ClientStore clients, PasswordHasher passwords, Transactions transactions) {
        this.clients = clients;
        this.passwords = passwords;
        this.transactions = transactions;
    }

    public void ensure(Map<String, String> secrets) {
        transactions.write(() -> {
            secrets.forEach((id, secret) -> {
                if (secret.isBlank()) return;
                if (secret.length() < 32) {
                    log.error("Service client configuration rejected, secret is too short: client_id={}", id);
                    throw new IllegalArgumentException("Service client secrets must contain at least 32 characters");
                }
                var client = clients.findById(id).orElse(new ServiceClient(id, null, true));
                if (client.secretHash() == null || !passwords.matches(secret, client.secretHash())) {
                    log.info("Configuring service client credentials: client_id={}", id);
                    clients.save(new ServiceClient(id, passwords.encode(secret), client.enabled()));
                }
            });
            return null;
        });
    }
}
