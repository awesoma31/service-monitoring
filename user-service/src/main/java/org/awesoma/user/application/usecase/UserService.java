package org.awesoma.user.application.usecase;

import java.util.List;
import org.awesoma.user.application.exception.UserConflict;
import org.awesoma.user.application.exception.UserNotFound;
import org.awesoma.user.application.model.UserPage;
import org.awesoma.user.application.port.PasswordHasher;
import org.awesoma.user.application.port.Transactions;
import org.awesoma.user.application.port.UserStore;
import org.awesoma.user.domain.User;
import org.awesoma.user.domain.enums.UserRole;
import org.awesoma.user.domain.enums.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** User account operations; callers and infrastructure meet through ports. */
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserStore users;
    private final PasswordHasher passwords;
    private final Transactions transactions;

    public UserService(UserStore users, PasswordHasher passwords, Transactions transactions) {
        this.users = users;
        this.passwords = passwords;
        this.transactions = transactions;
    }

    public UserPage list(int page, int size) {
        return transactions.read(() -> users.list(page, size));
    }

    public User get(long id) {
        return transactions.read(() -> require(id));
    }

    public User create(String email, String password, String fullName, UserRole role) {
        return transactions.write(() -> {
            if (users.existsByEmail(email)) {
                throw new UserConflict("Email %s is already taken".formatted(email));
            }
            var saved = users.save(new User(null, email, passwords.encode(password), fullName,
                    UserStatus.ACTIVE, role == null ? UserRole.VIEWER : role, false));
            logAfterCommit("CREATED", saved);
            return saved;
        });
    }

    public User update(long id, String fullName, UserStatus status) {
        return transactions.write(() -> {
            var saved = users.save(require(id).update(fullName, status));
            logAfterCommit("UPDATED", saved);
            return saved;
        });
    }

    public void delete(long id) {
        transactions.write(() -> {
            var saved = users.save(require(id).deactivate());
            logAfterCommit("DELETED", saved);
            return null;
        });
    }

    public User updateRole(long id, UserRole role) {
        return transactions.write(() -> {
            var saved = users.save(require(id).changeRole(role));
            logAfterCommit("ROLE_UPDATED", saved);
            return saved;
        });
    }

    /** Security lookup includes inactive or deleted accounts so peers can revoke access. */
    public User raw(long id) {
        return transactions.read(() -> users.findById(id).orElseThrow(() -> new UserNotFound(id)));
    }

    public List<User> batch(List<Long> ids) {
        return transactions.read(() -> users.findAllById(ids));
    }

    private User require(long id) {
        return users.findById(id).filter(user -> !user.deleted()).orElseThrow(() -> new UserNotFound(id));
    }

    private void logAfterCommit(String operation, User user) {
        transactions.afterCommit(() -> log.info("User changed: operation={} user_id={} role={} status={}",
                operation, user.id(), user.role(), user.status()));
    }
}
