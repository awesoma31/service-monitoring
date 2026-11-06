package org.awesoma.configserver.changes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.awesoma.configserver.changes.ConfigRepositoryScanner.Target;
import org.awesoma.integration.config.ConfigChanged;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;

/** Reads a service's settings the way a client would get them over HTTP. */
final class ConfigSnapshots {
    private final EnvironmentRepository repository;

    ConfigSnapshots(EnvironmentRepository repository) {
        this.repository = repository;
    }

    ConfigChanged read(Target target, long revision) {
        List<PropertySource> sources = new ArrayList<>(
                repository.findOne(target.service(), target.profile(), null).getPropertySources());
        // The first source has the highest priority, so it has to be applied last.
        Collections.reverse(sources);
        Map<String, String> properties = new TreeMap<>();
        for (PropertySource source : sources) {
            source.getSource().forEach((key, value) -> {
                String name = String.valueOf(key);
                if (!ConfigChanged.isSecret(name)) properties.put(name, String.valueOf(value));
            });
        }
        return new ConfigChanged(1, target.service(), target.profile(), revision, hash(properties), properties);
    }

    private static String hash(Map<String, String> properties) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(properties.toString().getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", error);
        }
    }
}
