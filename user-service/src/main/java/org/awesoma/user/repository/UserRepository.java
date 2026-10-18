package org.awesoma.user.repository;

import org.awesoma.user.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    java.util.Optional<User> findByEmail(String email);

    boolean existsByRole(org.awesoma.user.domain.enums.UserRole role);

    org.springframework.data.domain.Page<User> findByDeletedFalse(org.springframework.data.domain.Pageable pageable);

    boolean existsByEmail(String email);

}
