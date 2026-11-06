package org.awesoma.report.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Every stored field of a report, for adapters that save and load it. Unknown values are
 * {@code null}: a file's size and hash until it is ready, the generation fields for uploads.
 */
public record ReportSnapshot(
        UUID id,
        long projectId,
        long createdBy,
        ReportSource source,
        ReportFormat format,
        FileName fileName,
        ReportStatus status,
        Instant createdAt,
        Instant updatedAt,
        String objectKey,
        Long sizeBytes,
        String sha256,
        FailureCode failure,
        GenerationParameters generation,
        Lease lease,
        int attempts,
        Instant nextAttemptAt,
        Instant generatedAt,
        ReadBounds readBounds) {
}
