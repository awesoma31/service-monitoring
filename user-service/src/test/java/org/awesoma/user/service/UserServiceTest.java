package org.awesoma.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.awesoma.user.domain.entity.User;
import org.awesoma.user.domain.enums.UserStatus;
import org.awesoma.user.repository.UserRepository;
import org.awesoma.user.web.dto.user.UserCreateRequest;
import org.awesoma.user.web.dto.user.UserUpdateRequest;
import org.awesoma.user.web.exception.ConflictStateException;
import org.awesoma.user.web.exception.NotFoundException;
import org.awesoma.user.web.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository users;
    @Mock private UserMapper mapper;
    @Mock private PasswordEncoder passwords;
    @InjectMocks private UserService service;

    @Test
    void rejectsAnEmailThatIsAlreadyTaken() {
        when(users.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("taken@example.com")))
                .isInstanceOf(ConflictStateException.class)
                .hasMessageContaining("taken@example.com");

        verify(users, never()).save(any());
    }

    @Test
    void createSavesTheUserBuiltFromTheRequest() {
        User mapped = new User();
        when(users.existsByEmail("new@example.com")).thenReturn(false);
        when(mapper.toEntity(any())).thenReturn(mapped);
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwords.encode("plaintext-password")).thenReturn("encoded-password");

        service.create(request("new@example.com"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(mapped);
        assertThat(saved.getValue().getPassword()).isEqualTo("encoded-password");
    }

    @Test
    void reportsAMissingUserAsNotFound() {
        when(users.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void updateAppliesTheNewNameAndStatus() {
        User existing = new User();
        existing.setFullName("Old Name");
        when(users.findById(1L)).thenReturn(Optional.of(existing));

        service.update(1L, new UserUpdateRequest("New Name", UserStatus.BLOCKED));

        assertThat(existing.getFullName()).isEqualTo("New Name");
        assertThat(existing.getStatus()).isEqualTo(UserStatus.BLOCKED);
    }

    @Test
    void deleteRemovesAnExistingUser() {
        User existing = new User();
        when(users.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        assertThat(existing.isDeleted()).isTrue();
        assertThat(existing.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    private UserCreateRequest request(String email) {
        return new UserCreateRequest(email, "plaintext-password", "Full Name");
    }
}
