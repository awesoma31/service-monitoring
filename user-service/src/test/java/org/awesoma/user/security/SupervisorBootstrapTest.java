package org.awesoma.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.function.Supplier;
import org.awesoma.user.application.port.PasswordHasher;
import org.awesoma.user.application.port.Transactions;
import org.awesoma.user.application.port.UserStore;
import org.awesoma.user.application.usecase.SupervisorSetup;
import org.awesoma.user.domain.User;
import org.awesoma.user.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SupervisorBootstrapTest {
    private final UserStore users = mock(UserStore.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final Transactions transactions = new Transactions() {
        public <T> T read(Supplier<T> work) { return work.get(); }
        public <T> T write(Supplier<T> work) { return work.get(); }
        public void afterCommit(Runnable work) { work.run(); }
    };
    private final SupervisorSetup bootstrap = new SupervisorSetup(users, passwords, transactions);

    @Test
    void createsOnlyTheInitialSupervisorWithAHashedPassword() {
        when(passwords.encode("initial-password")).thenReturn("encoded");
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        bootstrap.ensure("supervisor@example.com", "initial-password");
        var saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().role()).isEqualTo(UserRole.SUPERVISOR);
        assertThat(saved.getValue().passwordHash()).isEqualTo("encoded");
    }

    @Test
    void neverResetsExistingSupervisorsOrCreatesOneWithoutCredentials() {
        when(users.existsByRole(UserRole.SUPERVISOR)).thenReturn(true, false);
        bootstrap.ensure("supervisor@example.com", "initial-password");
        bootstrap.ensure("supervisor@example.com", "");
        verify(users, never()).save(any());
    }

    @Test
    void refusesWeakPasswordsAndDoesNotPromoteAnExistingOrdinaryUser() {
        assertThatThrownBy(() -> bootstrap.ensure("supervisor@example.com", "short"))
                .isInstanceOf(IllegalArgumentException.class);
        when(users.existsByEmail("supervisor@example.com")).thenReturn(true);
        assertThatThrownBy(() -> bootstrap.ensure("supervisor@example.com", "initial-password"))
                .isInstanceOf(IllegalStateException.class);
    }
}
