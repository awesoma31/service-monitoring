package org.awesoma.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.function.Supplier;
import org.awesoma.user.application.exception.InvalidCredentials;
import org.awesoma.user.application.model.Token;
import org.awesoma.user.application.port.ClientStore;
import org.awesoma.user.application.port.PasswordHasher;
import org.awesoma.user.application.port.TokenIssuer;
import org.awesoma.user.application.port.Transactions;
import org.awesoma.user.application.port.UserStore;
import org.awesoma.user.application.usecase.AuthService;
import org.awesoma.user.application.usecase.ServiceAuthenticationService;
import org.awesoma.user.domain.ServiceClient;
import org.awesoma.user.domain.User;
import org.awesoma.user.domain.enums.UserRole;
import org.awesoma.user.domain.enums.UserStatus;
import org.junit.jupiter.api.Test;

class AuthUseCasesTest {
    private final UserStore users = mock(UserStore.class);
    private final ClientStore clients = mock(ClientStore.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final TokenIssuer tokens = mock(TokenIssuer.class);
    private final Transactions transactions = new Transactions() {
        public <T> T read(Supplier<T> work) { return work.get(); }
        public <T> T write(Supplier<T> work) { return work.get(); }
        public void afterCommit(Runnable work) { work.run(); }
    };

    @Test
    void absentUserStillChecksDummyHashWithoutIssuingToken() {
        when(passwords.encode(anyString())).thenReturn("dummy-hash");
        var auth = new AuthService(users, passwords, tokens, transactions);
        assertThatThrownBy(() -> auth.login("absent@example.com", "secret")).isInstanceOf(InvalidCredentials.class);
        verify(passwords).matches("secret", "dummy-hash");
        verifyNoInteractions(tokens);
    }

    @Test
    void activeUserGetsTokenButBlockedUserDoesNot() {
        when(passwords.encode(anyString())).thenReturn("dummy");
        when(passwords.matches("password", "hash")).thenReturn(true);
        var user = new User(7L, "a@example.com", "hash", "A", UserStatus.ACTIVE, UserRole.OPERATOR, false);
        when(users.findByEmail(user.email())).thenReturn(Optional.of(user), Optional.of(user.update("A", UserStatus.BLOCKED)));
        var token = new Token("token", "Bearer", 60);
        when(tokens.issue("7", "OPERATOR")).thenReturn(token);
        var auth = new AuthService(users, passwords, tokens, transactions);
        assertThat(auth.login(user.email(), "password")).isEqualTo(token);
        assertThatThrownBy(() -> auth.login(user.email(), "password")).isInstanceOf(InvalidCredentials.class);
        verify(tokens, times(1)).issue("7", "OPERATOR");
    }

    @Test
    void disabledServiceClientNeverGetsToken() {
        when(passwords.encode(anyString())).thenReturn("dummy");
        when(passwords.matches("secret", "hash")).thenReturn(true);
        when(clients.findById("worker")).thenReturn(Optional.of(new ServiceClient("worker", "hash", false)));
        var auth = new ServiceAuthenticationService(clients, passwords, tokens, transactions);
        assertThatThrownBy(() -> auth.authenticate("worker", "secret")).isInstanceOf(InvalidCredentials.class);
        verifyNoInteractions(tokens);
    }
}
