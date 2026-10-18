package org.awesoma.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.awesoma.user.domain.entity.User;
import org.awesoma.user.domain.enums.UserRole;
import org.awesoma.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SupervisorBootstrapTest {
    @Mock private UserRepository users;
    @Mock private PasswordEncoder passwords;
    @InjectMocks private SupervisorBootstrap bootstrap;

    @BeforeEach
    void properties() {
        ReflectionTestUtils.setField(bootstrap, "email", "supervisor@example.com");
        ReflectionTestUtils.setField(bootstrap, "password", "initial-password");
    }

    @Test
    void createsOnlyTheInitialSupervisorWithAHashedPassword() {
        when(passwords.encode("initial-password")).thenReturn("encoded");
        bootstrap.run(null);
        var saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.SUPERVISOR);
        assertThat(saved.getValue().getPassword()).isEqualTo("encoded");
    }

    @Test
    void neverResetsExistingSupervisorsOrCreatesOneWithoutCredentials() {
        when(users.existsByRole(UserRole.SUPERVISOR)).thenReturn(true);
        bootstrap.run(null);
        when(users.existsByRole(UserRole.SUPERVISOR)).thenReturn(false);
        ReflectionTestUtils.setField(bootstrap, "password", "");
        bootstrap.run(null);
        verify(users, never()).save(any());
    }

    @Test
    void refusesWeakPasswordsAndDoesNotPromoteAnExistingOrdinaryUser() {
        ReflectionTestUtils.setField(bootstrap, "password", "short");
        assertThatThrownBy(() -> bootstrap.run(null)).isInstanceOf(IllegalArgumentException.class);
        ReflectionTestUtils.setField(bootstrap, "password", "initial-password");
        when(users.existsByEmail("supervisor@example.com")).thenReturn(true);
        assertThatThrownBy(() -> bootstrap.run(null)).isInstanceOf(IllegalStateException.class);
    }
}
