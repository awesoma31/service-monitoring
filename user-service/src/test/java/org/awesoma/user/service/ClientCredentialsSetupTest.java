package org.awesoma.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.awesoma.user.application.port.ClientStore;
import org.awesoma.user.application.port.PasswordHasher;
import org.awesoma.user.application.port.Transactions;
import org.awesoma.user.application.usecase.ClientCredentialsSetup;
import org.awesoma.user.domain.ServiceClient;
import org.junit.jupiter.api.Test;

class ClientCredentialsSetupTest {
    private final ClientStore clients = mock(ClientStore.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final Transactions transactions = new Transactions() {
        public <T> T read(Supplier<T> work) { return work.get(); }
        public <T> T write(Supplier<T> work) { return work.get(); }
        public void afterCommit(Runnable work) { work.run(); }
    };
    private final ClientCredentialsSetup setup = new ClientCredentialsSetup(clients, passwords, transactions);

    @Test
    void changingASecretKeepsTheExistingDisabledStatus() {
        String secret = "s".repeat(32);
        when(clients.findById("worker")).thenReturn(Optional.of(new ServiceClient("worker", "old", false)));
        when(passwords.encode(secret)).thenReturn("new-hash");

        setup.ensure(Map.of("worker", secret));

        verify(clients).save(new ServiceClient("worker", "new-hash", false));
    }

    @Test
    void refusesShortSecretsWithoutSaving() {
        assertThatThrownBy(() -> setup.ensure(Map.of("worker", "too-short")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(clients, never()).save(any());
    }
}
