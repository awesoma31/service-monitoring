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
import lombok.extern.slf4j.Slf4j;

/** The private key persists across restarts and is never returned by the JWKS endpoint. */
@Slf4j
public class SigningKeyStore {
    public RSAKey loadOrCreate(Path path) {
        try {
            Path absolute = path.toAbsolutePath();
            Files.createDirectories(absolute.getParent());
            try (var channel = FileChannel.open(Path.of(absolute + ".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                if (Files.exists(absolute)) {
                    RSAKey key = RSAKey.parse(Files.readString(absolute));
                    log.info("JWT signing key loaded: path={} key_id={}", absolute, key.getKeyID());
                    return key;
                }
                var key = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
                Path temporary = Files.createTempFile(absolute.getParent(), "signing-", ".json");
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
                Files.writeString(temporary, key.toJSONString());
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE);
                log.info("JWT signing key created: path={} key_id={}", absolute, key.getKeyID());
                return key;
            }
        } catch (Exception error) {
            log.error("Cannot load JWT signing key: path={}", path, error);
            throw new IllegalStateException("Cannot load JWT signing key", error);
        }
    }
}
