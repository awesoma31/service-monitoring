package org.awesoma.integration.reportdata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Where a keyset walk over one source table stands: rows with {@code lastId < id <= upperId}
 * are still to come. The upper bound and the capture time are fixed by the first page, so
 * rows added during the walk are left out of it.
 *
 * <p>The token is opaque to the reader and carries a hash of the query it belongs to, so a
 * cursor cannot be applied to another project, selection or period. It is not encrypted:
 * only services with service credentials may call the endpoints, and a forged position
 * could at most skip rows of the very query the caller is allowed to make.
 */
public record ReportDataCursor(long lastId, long upperId, Instant capturedAt) {
    private static final String VERSION = "v1";

    /** The capture time keeps the precision the token can carry, so every page reports the same. */
    public static ReportDataCursor start(long upperId, Instant capturedAt) {
        return new ReportDataCursor(0, upperId, capturedAt.truncatedTo(ChronoUnit.MILLIS));
    }

    public ReportDataCursor after(long id) {
        return new ReportDataCursor(id, upperId, capturedAt);
    }

    public String encode(String scope) {
        String raw = String.join("|", VERSION, Long.toString(lastId), Long.toString(upperId),
                Long.toString(capturedAt.toEpochMilli()), fingerprint(scope));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws IllegalArgumentException when the token is malformed or made for another query */
    public static ReportDataCursor decode(String token, String scope) {
        String[] parts;
        try {
            parts = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8).split("\\|");
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("Malformed cursor", malformed);
        }
        if (parts.length != 5 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Malformed cursor");
        }
        if (!fingerprint(scope).equals(parts[4])) {
            throw new IllegalArgumentException("Cursor belongs to another query");
        }
        try {
            var cursor = new ReportDataCursor(Long.parseLong(parts[1]), Long.parseLong(parts[2]),
                    Instant.ofEpochMilli(Long.parseLong(parts[3])));
            if (cursor.lastId < 0 || cursor.lastId > cursor.upperId) throw new IllegalArgumentException();
            return cursor;
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("Malformed cursor", malformed);
        }
    }

    private static String fingerprint(String scope) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", error);
        }
    }
}
