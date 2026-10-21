package org.awesoma.user.repository;

import java.util.Optional;
import org.awesoma.user.domain.entity.User;
import org.awesoma.user.domain.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByRole(UserRole role);

    Page<User> findByDeletedFalse(Pageable pageable);

    boolean existsByEmail(String email);

}
