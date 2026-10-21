package org.awesoma.user.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;

/** The private key persists across restarts and is never returned by the JWKS endpoint. */
public class SigningKeyStore {
    public RSAKey loadOrCreate(Path path) {
        try {
            Path absolute = path.toAbsolutePath();
            Files.createDirectories(absolute.getParent());
            try (var channel = FileChannel.open(Path.of(absolute + ".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                if (Files.exists(absolute)) return RSAKey.parse(Files.readString(absolute));
                var key = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
                Path temporary = Files.createTempFile(absolute.getParent(), "signing-", ".json");
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
                Files.writeString(temporary, key.toJSONString());
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE);
                return key;
            }
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load JWT signing key", error);
        }
    }
}
