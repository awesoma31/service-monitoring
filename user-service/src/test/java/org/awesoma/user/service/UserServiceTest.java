package org.awesoma.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.function.Supplier;
import org.awesoma.user.application.exception.UserConflict;
import org.awesoma.user.application.exception.UserNotFound;
import org.awesoma.user.application.port.PasswordHasher;
import org.awesoma.user.application.port.Transactions;
import org.awesoma.user.application.port.UserStore;
import org.awesoma.user.application.usecase.UserService;
import org.awesoma.user.domain.User;
import org.awesoma.user.domain.enums.UserRole;
import org.awesoma.user.domain.enums.UserStatus;
import org.junit.jupiter.api.Test;

class UserServiceTest {
    private final UserStore users=mock(UserStore.class);
    private final PasswordHasher passwords=mock(PasswordHasher.class);
    private final Transactions tx=new Transactions() {
        public <T> T read(Supplier<T> work) { return work.get(); }
        public <T> T write(Supplier<T> work) { return work.get(); }
        public void afterCommit(Runnable work) { work.run(); }
    };
    private final UserService service=new UserService(users,passwords,tx);
    private User account() { return new User(1L,"a@example.com","hash","Old",UserStatus.ACTIVE,UserRole.VIEWER,false); }
    @Test void rejectsDuplicate() {
        when(users.existsByEmail("a@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.create("a@example.com","password","A",UserRole.VIEWER)).isInstanceOf(UserConflict.class);
        verify(users,never()).save(any());
    }
    @Test void hashesPasswordBeforeSaving() {
        when(passwords.encode("password")).thenReturn("encoded");
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.create("a@example.com","password","A",UserRole.VIEWER).passwordHash()).isEqualTo("encoded");
    }
    @Test void absentAndDeletedUsersAreNotFound() {
        assertThatThrownBy(() -> service.get(404)).isInstanceOf(UserNotFound.class);
        when(users.findById(1)).thenReturn(Optional.of(account().deactivate()));
        assertThatThrownBy(() -> service.get(1)).isInstanceOf(UserNotFound.class);
    }
    @Test void updatesAndSoftDeleteAreExplicitlySaved() {
        when(users.findById(1)).thenReturn(Optional.of(account()));
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.update(1,"New",UserStatus.BLOCKED).status()).isEqualTo(UserStatus.BLOCKED);
        service.delete(1);
        verify(users).save(account().deactivate());
        assertThat(service.updateRole(1,UserRole.OPERATOR).role()).isEqualTo(UserRole.OPERATOR);
    }
}
