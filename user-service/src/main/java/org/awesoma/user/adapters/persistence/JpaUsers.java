package org.awesoma.user.adapters.persistence;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.awesoma.user.application.model.UserPage;
import org.awesoma.user.application.port.UserStore;
import org.awesoma.user.domain.User;
import org.awesoma.user.domain.enums.UserRole;
import org.awesoma.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Maps persistence entities to domain accounts at the database boundary. */
@Component
@RequiredArgsConstructor
public class JpaUsers implements UserStore {
    private final UserRepository rows;

    @Override
    public Optional<User> findById(long id) {
        return rows.findById(id).map(JpaUsers::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return rows.findByEmail(email).map(JpaUsers::toDomain);
    }

    @Override
    public boolean existsByRole(UserRole role) {
        return rows.existsByRole(role);
    }

    @Override
    public boolean existsByEmail(String email) {
        return rows.existsByEmail(email);
    }

    @Override
    public UserPage list(int page, int size) {
        var result = rows.findByDeletedFalse(PageRequest.of(page, size));
        return new UserPage(result.getContent().stream().map(JpaUsers::toDomain).toList(),
                result.getTotalElements(), page, size);
    }

    @Override
    public List<User> findAllById(List<Long> ids) {
        return rows.findAllById(ids).stream().map(JpaUsers::toDomain).toList();
    }

    @Override
    public User save(User user) {
        var row = user.id() == null ? new org.awesoma.user.adapters.persistence.User()
                : rows.findById(user.id()).orElseThrow();
        row.setEmail(user.email());
        row.setPassword(user.passwordHash());
        row.setFullName(user.fullName());
        row.setStatus(user.status());
        row.setRole(user.role());
        row.setDeleted(user.deleted());
        return toDomain(rows.save(row));
    }

    private static User toDomain(org.awesoma.user.adapters.persistence.User row) {
        return new User(row.getId(), row.getEmail(), row.getPassword(), row.getFullName(),
                row.getStatus(), row.getRole(), row.isDeleted());
    }
}
