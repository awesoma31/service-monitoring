package org.awesoma.configserver.changes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.awesoma.integration.config.ConfigChanged;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/** Finds which services and profiles the repository holds, and refuses it while a file is broken. */
final class ConfigRepositoryScanner {
    private final Path root;

    ConfigRepositoryScanner(Path root) {
        this.root = root;
    }

    record Target(String service, String profile) {
    }

    record Scan(List<Target> targets, String fingerprint) {
    }

    /** @throws IOException when a file cannot be read or is not valid YAML */
    Scan scan() throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            // A mounted Kubernetes-style link or an editor's temporary file is not a config file.
            files = walk.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().matches("[^.].*\\.ya?ml"))
                    .sorted().toList();
        }
        var digest = sha256();
        for (Path file : files) {
            byte[] content = Files.readAllBytes(file);
            validate(file, content);
            digest.update(root.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
            digest.update(content);
        }
        return new Scan(targets(files), HexFormat.of().formatHex(digest.digest()));
    }

    private List<Target> targets(List<Path> files) {
        Set<String> shared = new TreeSet<>();
        for (Path file : files) {
            if (root.equals(file.getParent())) profileOf(file, "application", shared);
        }
        List<Target> targets = new ArrayList<>();
        for (Path file : files) {
            String service = file.getParent().getFileName().toString();
            if (root.equals(file.getParent()) || !stem(file).equals(service)) continue;
            Set<String> profiles = new TreeSet<>(shared);
            profiles.add(ConfigChanged.DEFAULT_PROFILE);
            for (Path other : files) {
                if (file.getParent().equals(other.getParent())) profileOf(other, service, profiles);
            }
            profiles.forEach(profile -> targets.add(new Target(service, profile)));
        }
        return targets;
    }

    private static void profileOf(Path file, String name, Set<String> profiles) {
        Matcher matcher = Pattern.compile(Pattern.quote(name) + "-(.+)").matcher(stem(file));
        if (matcher.matches()) profiles.add(matcher.group(1));
    }

    private static String stem(Path file) {
        return file.getFileName().toString().replaceFirst("\\.ya?ml$", "");
    }

    private static void validate(Path file, byte[] content) throws IOException {
        try {
            new Yaml().loadAll(new String(content, StandardCharsets.UTF_8)).forEach(document -> { });
        } catch (YAMLException invalid) {
            throw new IOException("Invalid YAML in " + file.getFileName() + ": " + invalid.getMessage(), invalid);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", error);
        }
    }
}
