package org.awesoma.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SigningKeyStoreTest {
    @TempDir Path directory;

    @Test
    void persistsThePrivateKeyAcrossRestartsAndOnlyPublishesPublicParts() throws Exception {
        Path file = directory.resolve("keys/signing.json");
        var first = new SigningKeyStore().loadOrCreate(file);
        var second = new SigningKeyStore().loadOrCreate(file);
        assertThat(first.toJSONString()).isEqualTo(second.toJSONString());
        assertThat(first.isPrivate()).isTrue();
        assertThat(first.toPublicJWK().isPrivate()).isFalse();
        assertThat(Files.getPosixFilePermissions(file)).hasSize(2);
    }

    @Test
    void refusesToReplaceAnUnreadableExistingKey() throws Exception {
        Path file = directory.resolve("broken.json");
        Files.writeString(file, "invalid-key");
        assertThatThrownBy(() -> new SigningKeyStore().loadOrCreate(file)).isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(file)).isEqualTo("invalid-key");
    }
}
