package org.awesoma.user.application.usecase;

import java.util.UUID;
import org.awesoma.user.application.exception.InvalidCredentials;
import org.awesoma.user.application.model.Token;
import org.awesoma.user.application.port.PasswordHasher;
import org.awesoma.user.application.port.TokenIssuer;
import org.awesoma.user.application.port.Transactions;
import org.awesoma.user.application.port.UserStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Authenticates users without depending on a password or token implementation. */
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserStore users;
    private final PasswordHasher passwords;
    private final TokenIssuer tokens;
    private final Transactions transactions;
    private final String dummyHash;

    public AuthService(UserStore users, PasswordHasher passwords, TokenIssuer tokens, Transactions transactions) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.transactions = transactions;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public Token login(String email, String password) {
        return transactions.read(() -> {
            var user = users.findByEmail(email);
            boolean matches = passwords.matches(password, user.map(value -> value.passwordHash()).orElse(dummyHash));
            if (user.isEmpty() || !matches || !user.get().canAuthenticate()) {
                log.warn("User login rejected: user_id={}", user.map(value -> value.id()).orElse(null));
                throw new InvalidCredentials();
            }
            var result = tokens.issue(user.get().id().toString(), user.get().role().name());
            log.info("User login succeeded: user_id={}", user.get().id());
            return result;
        });
    }
}
