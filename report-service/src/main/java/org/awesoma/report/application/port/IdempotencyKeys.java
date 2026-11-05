package org.awesoma.report.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Bindings of an Idempotency-Key to the job it created, scoped by user and project. A binding
 * lives as long as its report, then as a tombstone until {@code expiresAt}.
 */
public interface IdempotencyKeys {

    Optional<IdempotencyRecord> find(long userId, long projectId, String key);

    /** @return {@code false} when the key is already bound, for example by a concurrent request */
    boolean bind(IdempotencyRecord record);

    /** Remove only the expired binding observed by the caller. */
    boolean removeExpired(IdempotencyRecord observed, Instant now);

    /** Turns the bindings of a removed report into tombstones that expire at the given time. */
    void tombstone(UUID reportId, Instant expiresAt);

    int removeExpired(Instant now);

    record IdempotencyRecord(long userId, long projectId, String key, String fingerprint, UUID reportId,
            Instant expiresAt) {

        public boolean tombstoneAt(Instant now) {
            return expiresAt != null && expiresAt.isAfter(now);
        }

        public boolean expiredAt(Instant now) {
            return expiresAt != null && !expiresAt.isAfter(now);
        }
    }
}
