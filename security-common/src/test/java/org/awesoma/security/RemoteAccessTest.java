package org.awesoma.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

class RemoteAccessTest {
    private final ResourceAccessClient client = mock(ResourceAccessClient.class);
    private final RemoteAccess access = new RemoteAccess(client);
    private final Jwt user = Jwt.withTokenValue("test").header("alg", "HS256").subject("42").build();

    @Test
    void usesTheVerifiedUserIdentityAndWriteFlag() {
        when(client.allowed("project", 7L, 42L, true)).thenReturn(true);
        StepVerifier.create(access.require("project", 7L, user, true)).verifyComplete();
    }

    @Test
    void rejectsForeignResourcesAndFailsClosedDuringAnOutage() {
        when(client.allowed("project", 7L, 42L, false)).thenReturn(false);
        StepVerifier.create(access.require("project", 7L, user, false)).expectError(AccessDeniedException.class)
                .verify();
        when(client.allowed("project", 7L, 42L, false)).thenThrow(new IllegalStateException("down"));
        StepVerifier.create(access.require("project", 7L, user, false))
                .expectErrorMatches(error -> error instanceof ResponseStatusException status && status.getStatusCode()
                .value() == 503)
                .verify();
    }
}
