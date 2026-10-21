package org.awesoma.security;

import java.util.Map;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

/** PBKDF2 supports the existing 255-character passwords without BCrypt's 72-byte limit. */
public final class PasswordHashing {
    private PasswordHashing() {}

    public static PasswordEncoder encoder() {
        String algorithm = "pbkdf2@SpringSecurity_v5_8";
        return new DelegatingPasswordEncoder(algorithm,
                Map.of(algorithm, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }
}
