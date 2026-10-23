package org.awesoma.security.password;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHashingTest {
    @Test
    void hashesPasswordsWithDistinctSaltsIncludingLongPasswords() {
        var passwords = PasswordHashing.encoder();
        String plain = "long-password".repeat(15);
        String first = passwords.encode(plain);
        String second = passwords.encode(plain);
        assertThat(first).isNotEqualTo(plain).isNotEqualTo(second);
        assertThat(passwords.matches(plain, first)).isTrue();
        assertThat(passwords.matches("wrong", first)).isFalse();
    }
}
