package org.awesoma.user.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.user.domain.entity.User;
import org.awesoma.user.domain.enums.UserRole;
import org.awesoma.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@org.springframework.core.annotation.Order(20)
public class SupervisorBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    @Value("${security.bootstrap.email:supervisor@example.com}") private String email;
    @Value("${security.bootstrap.password:}") private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(UserRole.SUPERVISOR) || password.isBlank()) return;
        if (password.length() < 8 || password.length() > 255) {
            throw new IllegalArgumentException("SUPERVISOR_PASSWORD must contain 8 to 255 characters");
        }
        if (users.existsByEmail(email)) {
            throw new IllegalStateException("Bootstrap email already belongs to a non-supervisor; choose another email");
        }
        User user = new User();
        user.setEmail(email);
        user.setFullName("Supervisor");
        user.setRole(UserRole.SUPERVISOR);
        user.setPassword(passwords.encode(password));
        users.save(user);
        log.info("Initial supervisor created; subsequent users must be created by a supervisor");
    }
}
