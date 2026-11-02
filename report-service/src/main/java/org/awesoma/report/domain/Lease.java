package org.awesoma.report.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * The right of one upload or generation attempt to finish the report. Every attempt has its
 * own ID, and so its own object key; the token proves that the caller still holds the lease.
 */
public record Lease(UUID attemptId, UUID token, Instant until) {

    public boolean heldAt(Instant now) {
        return now.isBefore(until);
    }

    public Lease extendedTo(Instant newUntil) {
        return new Lease(attemptId, token, newUntil);
    }
}
