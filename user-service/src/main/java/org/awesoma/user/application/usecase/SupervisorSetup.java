package org.awesoma.user.application.usecase;

import org.awesoma.user.application.port.PasswordHasher;
import org.awesoma.user.application.port.Transactions;
import org.awesoma.user.application.port.UserStore;
import org.awesoma.user.domain.User;
import org.awesoma.user.domain.enums.UserRole;
import org.awesoma.user.domain.enums.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates the first supervisor exactly when no supervisor has been configured. */
public class SupervisorSetup {
    private static final Logger log = LoggerFactory.getLogger(SupervisorSetup.class);
    private final UserStore users;
    private final PasswordHasher passwords;
    private final Transactions transactions;

    public SupervisorSetup(UserStore users, PasswordHasher passwords, Transactions transactions) {
        this.users = users;
        this.passwords = passwords;
        this.transactions = transactions;
    }

    public void ensure(String email, String password) {
        transactions.write(() -> {
            if (users.existsByRole(UserRole.SUPERVISOR) || password.isBlank()) return null;
            if (password.length() < 8 || password.length() > 255) {
                log.error("Supervisor bootstrap rejected: reason=invalid_password_length");
                throw new IllegalArgumentException("SUPERVISOR_PASSWORD must contain 8 to 255 characters");
            }
            if (users.existsByEmail(email)) {
                log.error("Supervisor bootstrap rejected: reason=email_already_taken");
                throw new IllegalStateException("Bootstrap email already belongs to a non-supervisor; choose another email");
            }
            User user = users.save(new User(null, email, passwords.encode(password), "Supervisor",
                    UserStatus.ACTIVE, UserRole.SUPERVISOR, false));
            log.info("Initial supervisor creation requested: user_id={}", user.id());
            return null;
        });
    }
}
