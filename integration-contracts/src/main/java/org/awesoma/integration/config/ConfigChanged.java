package org.awesoma.integration.config;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * The full settings of one service under one profile, as a snapshot rather than a difference, so
 * that a removed key shows and only the latest record of a key matters in the compacted topic.
 * Values are as written in the files: a {@code ${...}} placeholder is resolved by the receiver.
 * Secret keys are left out on both sides.
 *
 * @param revision grows with every publication; a smaller one is out of date
 */
public record ConfigChanged(int schemaVersion, String service, String profile, long revision, String contentHash,
        Map<String, String> properties) {
    public static final String TOPIC = "config.changes.v1";
    public static final String DEFAULT_PROFILE = "default";
    private static final Pattern SECRET = Pattern.compile("(?i).*(password|secret|token|credential|access-key).*");

    public static boolean isSecret(String key) {
        return SECRET.matcher(key).matches();
    }

    public String key() {
        return service + "/" + profile;
    }

    public void validate() {
        if (schemaVersion != 1 || service == null || service.isBlank() || profile == null || profile.isBlank()
                || revision <= 0 || contentHash == null || properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("Invalid configuration snapshot");
        }
    }
}
