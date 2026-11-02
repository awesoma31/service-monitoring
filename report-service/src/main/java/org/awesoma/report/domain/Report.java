package org.awesoma.report.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A stored or generated report file and its lifecycle. Every change checks the current
 * state, and changes made for an attempt also check its lease, so an attempt that has lost
 * its lease cannot bring a report back to READY.
 */
public final class Report {
    private final UUID id;
    private final long projectId;
    private final long createdBy;
    private final ReportSource source;
    private final ReportFormat format;
    private final FileName fileName;
    private final Instant createdAt;
    private final GenerationParameters generation;
    private ReportStatus status;
    private Instant updatedAt;
    private String objectKey;
    private Long sizeBytes;
    private String sha256;
    private FailureCode failure;
    private Lease lease;
    private int attempts;
    private Instant nextAttemptAt;
    private Instant generatedAt;
    private ReadBounds readBounds;

    private Report(ReportSnapshot snapshot) {
        this.id = Objects.requireNonNull(snapshot.id());
        this.projectId = snapshot.projectId();
        this.createdBy = snapshot.createdBy();
        this.source = Objects.requireNonNull(snapshot.source());
        this.format = Objects.requireNonNull(snapshot.format());
        this.fileName = Objects.requireNonNull(snapshot.fileName());
        this.status = Objects.requireNonNull(snapshot.status());
        this.createdAt = Objects.requireNonNull(snapshot.createdAt());
        this.updatedAt = Objects.requireNonNull(snapshot.updatedAt());
        this.objectKey = snapshot.objectKey();
        this.sizeBytes = snapshot.sizeBytes();
        this.sha256 = snapshot.sha256();
        this.failure = snapshot.failure();
        this.generation = snapshot.generation();
        this.lease = snapshot.lease();
        this.attempts = snapshot.attempts();
        this.nextAttemptAt = snapshot.nextAttemptAt();
        this.generatedAt = snapshot.generatedAt();
        this.readBounds = snapshot.readBounds();
        if ((source == ReportSource.GENERATED) != (generation != null)) {
            throw new IllegalArgumentException("Only generated reports carry generation parameters");
        }
    }

    public static Report restore(ReportSnapshot snapshot) {
        return new Report(snapshot);
    }

    /** An upload starts with its own key and lease; the file is stored after this is saved. */
    public static Report startUpload(UUID id, long projectId, long userId, FileName fileName, Lease lease,
            Instant now) {
        var format = ReportFormat.fromExtension(fileName.extension()).orElseThrow(() ->
                new InvalidReportRequestException("file", "Only .csv and .pdf files are accepted"));
        return new Report(new ReportSnapshot(id, projectId, userId, ReportSource.UPLOADED, format, fileName,
                ReportStatus.UPLOADING, now, now, ObjectKeys.upload(projectId, id), null, null, null, null,
                Objects.requireNonNull(lease), 1, null, null, null));
    }

    /** The service names generated files itself, from the report ID. */
    public static Report queueGeneration(UUID id, long projectId, long userId, ReportFormat format,
            GenerationParameters parameters) {
        var name = new FileName("report-" + id + "." + format.extension());
        Instant now = parameters.requestedAt();
        return new Report(new ReportSnapshot(id, projectId, userId, ReportSource.GENERATED, format, name,
                ReportStatus.QUEUED, now, now, null, null, null, null, Objects.requireNonNull(parameters), null, 0,
                null, null, null));
    }

    public void completeUpload(UUID token, long size, String hash, Instant now) {
        requireSource(ReportSource.UPLOADED);
        requireAttempt(ReportStatus.UPLOADING, token, now);
        publish(size, hash, now);
    }

    /** The key stays recorded so that cleanup can remove whatever reached the storage. */
    public void failUpload(UUID token, Instant now) {
        requireSource(ReportSource.UPLOADED);
        requireCurrentToken(ReportStatus.UPLOADING, token);
        moveTo(ReportStatus.FAILED, now);
        failure = FailureCode.UPLOAD_FAILED;
        lease = null;
    }

    public boolean dueAt(Instant now) {
        return status == ReportStatus.QUEUED && (nextAttemptAt == null || !nextAttemptAt.isAfter(now));
    }

    /** Each attempt starts from scratch: new key, new lease, no bounds of an earlier attempt. */
    public void startAttempt(Lease newLease, Instant now) {
        requireSource(ReportSource.GENERATED);
        if (!dueAt(now)) {
            throw new ReportStateException("Report " + id + " is not due for generation");
        }
        moveTo(ReportStatus.GENERATING, now);
        lease = Objects.requireNonNull(newLease);
        attempts++;
        objectKey = ObjectKeys.attempt(projectId, id, newLease.attemptId());
        nextAttemptAt = null;
        readBounds = null;
        failure = null;
    }

    public void renewLease(UUID token, Instant until, Instant now) {
        requireAttempt(ReportStatus.GENERATING, token, now);
        lease = lease.extendedTo(until);
        updatedAt = now;
    }

    public void recordReadBounds(UUID token, ReadBounds bounds, Instant now) {
        requireAttempt(ReportStatus.GENERATING, token, now);
        readBounds = Objects.requireNonNull(bounds);
        updatedAt = now;
    }

    public void completeGeneration(UUID token, long size, String hash, Instant now) {
        requireSource(ReportSource.GENERATED);
        requireAttempt(ReportStatus.GENERATING, token, now);
        generatedAt = now;
        publish(size, hash, now);
    }

    /**
     * A temporary failure waits {@code initialDelay × 2^(attempts − 1)} before the next attempt;
     * once {@code maxAttempts} are used up the report fails with the given reason.
     */
    public void retryLater(UUID token, FailureCode reason, Duration initialDelay, int maxAttempts, Instant now) {
        requireCurrentToken(ReportStatus.GENERATING, token);
        scheduleRetry(reason, initialDelay, maxAttempts, now);
    }

    /** A failure that another attempt would not fix, such as a revoked right or an exceeded limit. */
    public void fail(UUID token, FailureCode reason, Instant now) {
        requireCurrentToken(ReportStatus.GENERATING, token);
        moveTo(ReportStatus.FAILED, now);
        failure = Objects.requireNonNull(reason);
        lease = null;
    }

    /** Recovery after a worker died: an expired lease counts as a temporary failure. */
    public void expireLease(FailureCode reason, Duration initialDelay, int maxAttempts, Instant now) {
        if (status != ReportStatus.GENERATING || lease == null || lease.heldAt(now)) {
            throw new ReportStateException("Report " + id + " has no expired lease");
        }
        scheduleRetry(reason, initialDelay, maxAttempts, now);
    }

    /**
     * Hides the report and revokes any lease. Deleting again is allowed and changes nothing.
     *
     * @return whether this call started the deletion
     */
    public boolean markDeleting(Instant now) {
        if (status == ReportStatus.DELETING) return false;
        moveTo(ReportStatus.DELETING, now);
        lease = null;
        nextAttemptAt = null;
        return true;
    }

    /** Only a confirmed absence counts; a storage timeout says nothing about the object. */
    public void markObjectMissing(Instant now) {
        if (status != ReportStatus.READY) {
            throw new ReportStateException("Report " + id + " is not ready");
        }
        moveTo(ReportStatus.FAILED, now);
        failure = FailureCode.OBJECT_MISSING;
    }

    public boolean visible() {
        return status.visible();
    }

    public boolean contentAvailable() {
        return status == ReportStatus.READY;
    }

    public ReportSnapshot snapshot() {
        return new ReportSnapshot(id, projectId, createdBy, source, format, fileName, status, createdAt, updatedAt,
                objectKey, sizeBytes, sha256, failure, generation, lease, attempts, nextAttemptAt, generatedAt,
                readBounds);
    }

    public UUID id() { return id; }
    public long projectId() { return projectId; }
    public long createdBy() { return createdBy; }
    public ReportSource source() { return source; }
    public ReportFormat format() { return format; }
    public FileName fileName() { return fileName; }
    public ReportStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public String objectKey() { return objectKey; }
    public Long sizeBytes() { return sizeBytes; }
    public String sha256() { return sha256; }
    public FailureCode failure() { return failure; }
    public GenerationParameters generation() { return generation; }
    public Lease lease() { return lease; }
    public int attempts() { return attempts; }
    public Instant nextAttemptAt() { return nextAttemptAt; }
    public Instant generatedAt() { return generatedAt; }
    public ReadBounds readBounds() { return readBounds; }

    private void scheduleRetry(FailureCode reason, Duration initialDelay, int maxAttempts, Instant now) {
        lease = null;
        if (attempts >= maxAttempts) {
            moveTo(ReportStatus.FAILED, now);
            failure = Objects.requireNonNull(reason);
            return;
        }
        moveTo(ReportStatus.QUEUED, now);
        nextAttemptAt = now.plus(initialDelay.multipliedBy(1L << Math.max(0, attempts - 1)));
    }

    private void publish(long size, String hash, Instant now) {
        if (size <= 0 || hash == null || hash.length() != 64) {
            throw new IllegalArgumentException("A ready report needs its size and SHA-256");
        }
        moveTo(ReportStatus.READY, now);
        sizeBytes = size;
        sha256 = hash;
        lease = null;
    }

    private void requireSource(ReportSource expected) {
        if (source != expected) {
            throw new ReportStateException("Report " + id + " is not " + expected);
        }
    }

    private void requireAttempt(ReportStatus expected, UUID token, Instant now) {
        requireCurrentToken(expected, token);
        if (!lease.heldAt(now)) {
            throw new StaleAttemptException("Lease of report " + id + " has expired");
        }
    }

    private void requireCurrentToken(ReportStatus expected, UUID token) {
        if (status != expected || lease == null || !lease.token().equals(token)) {
            throw new StaleAttemptException("Attempt is no longer current for report " + id);
        }
    }

    private void moveTo(ReportStatus target, Instant now) {
        if (!status.canMoveTo(target)) {
            throw new ReportStateException("Report " + id + " cannot move from " + status + " to " + target);
        }
        status = target;
        updatedAt = now;
    }
}
