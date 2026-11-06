package org.awesoma.user.domain;

import org.awesoma.user.domain.enums.UserRole;
import org.awesoma.user.domain.enums.UserStatus;

/** Business representation of an account, independent of its storage. */
public record User(Long id, String email, String passwordHash, String fullName,
                   UserStatus status, UserRole role, boolean deleted) {
    public User update(String name, UserStatus newStatus) {
        return new User(id, email, passwordHash, name, newStatus, role, deleted);
    }
    public User changeRole(UserRole newRole) {
        return new User(id, email, passwordHash, fullName, status, newRole, deleted);
    }
    public User deactivate() {
        return new User(id, email, passwordHash, fullName, UserStatus.INACTIVE, role, true);
    }
    public boolean canAuthenticate() { return !deleted && status == UserStatus.ACTIVE; }
}
