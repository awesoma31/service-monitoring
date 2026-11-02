package org.awesoma.report.application.usecase;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import org.awesoma.report.application.content.ContentTooLargeException;
import org.awesoma.report.application.content.LimitedOutputStream;
import org.awesoma.report.application.content.MeasuredInputStream;
import org.awesoma.report.application.port.AccessDecision;
import org.awesoma.report.application.port.AccessLevel;
import org.awesoma.report.application.port.AccessUnavailableException;
import org.awesoma.report.application.port.ObjectStorage;
import org.awesoma.report.application.port.ObjectStorageUnavailableException;
import org.awesoma.report.application.port.ProjectAccessPort;
import org.awesoma.report.application.port.ProjectGuard;
import org.awesoma.report.application.port.ProjectNotFoundException;
import org.awesoma.report.application.port.ReportDataSource;
import org.awesoma.report.application.port.ReportJobQueue;
import org.awesoma.report.application.port.ReportRenderer;
import org.awesoma.report.application.port.ReportRepository;
import org.awesoma.report.application.port.SourcePage;
import org.awesoma.report.application.port.SourceRejectedException;
import org.awesoma.report.application.port.SourceUnavailableException;
import org.awesoma.report.application.port.TransactionRunner;
import org.awesoma.report.domain.FailureCode;
import org.awesoma.report.domain.Lease;
import org.awesoma.report.domain.ReadBounds;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportFormat;
import org.awesoma.report.domain.ReportStatus;
import org.awesoma.report.domain.StaleAttemptException;
import org.awesoma.report.domain.content.ReportAggregation;
import org.awesoma.report.domain.source.MonitorInfo;
import org.awesoma.report.domain.source.ProjectInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds queued reports. An attempt claims a job with a lease, reads the sources page by page
 * into a fresh aggregation, renders the file into a bounded temporary file, stores it under the
 * attempt's own key and publishes it only while it still holds the lease. Network calls, the
 * rendering and the upload all run outside any transaction; the lease is renewed between pages.
 *
 * <p>A temporary failure puts the job back in the queue with a growing delay; a lasting one,
 * such as a revoked right or an exceeded limit, fails it at once. A partial report is never
 * published: the object of an attempt that does not finish is removed.
 */
public class GenerationWorker {
    private static final Logger log = LoggerFactory.getLogger(GenerationWorker.class);

    private final ReportRepository reports;
    private final ReportJobQueue queue;
    private final ReportDataSource sources;
    private final ProjectAccessPort access;
    private final ProjectGuard projects;
    private final ObjectStorage storage;
    private final Map<ReportFormat, ReportRenderer> renderers = new EnumMap<>(ReportFormat.class);
    private final ReportCleanup cleanup;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final GenerationLimits limits;
    private final long maxFileSize;

    public GenerationWorker(ReportRepository reports, ReportJobQueue queue, ReportDataSource sources,
            ProjectAccessPort access, ProjectGuard projects, ObjectStorage storage, List<ReportRenderer> renderers,
            ReportCleanup cleanup, TransactionRunner transactions, Clock clock, GenerationLimits limits,
            long maxFileSize) {
        this.reports = reports;
        this.queue = queue;
        this.sources = sources;
        this.access = access;
        this.projects = projects;
        this.storage = storage;
        renderers.forEach(renderer -> this.renderers.put(renderer.format(), renderer));
        this.cleanup = cleanup;
        this.transactions = transactions;
        this.clock = clock;
        this.limits = limits;
        this.maxFileSize = maxFileSize;
    }

    /** @return whether a job was claimed and worked on */
    public boolean runNext() {
        var now = clock.instant();
        var lease = new Lease(UUID.randomUUID(), UUID.randomUUID(), now.plus(limits.leaseDuration()));
        var claimed = queue.claimNext(lease, now);
        claimed.ifPresent(report -> run(new Attempt(report, lease, now.plus(limits.attemptTimeout()))));
        return claimed.isPresent();
    }

    /** Jobs whose worker stopped renewing its lease go back to the queue, or fail if out of attempts. */
    public int recoverExpired(int limit) {
        int recovered = 0;
        for (UUID id : queue.expiredLeases(clock.instant(), limit)) {
            String abandoned = transactions.inTransaction(() -> reports.lockById(id)
                    .filter(current -> current.status() == ReportStatus.GENERATING && current.lease() != null
                            && !current.lease().heldAt(clock.instant()))
                    .map(current -> {
                        String key = current.objectKey();
                        current.expireLease(FailureCode.TIMED_OUT, limits.retryDelay(), limits.maxAttempts(),
                                clock.instant());
                        reports.save(current);
                        return key;
                    })
                    .orElse(null));
            if (abandoned != null) {
                log.warn("Generation lease expired, job recovered: report_id={}", id);
                cleanup.removeObject(abandoned);
                recovered++;
            }
        }
        return recovered;
    }

    private void run(Attempt attempt) {
        Report report = attempt.report;
        Path file = null;
        try {
            requireAuthor(report);
            var aggregation = collect(attempt);
            file = Files.createTempFile("report-" + report.id(), "." + report.format().extension());
            render(report, aggregation, file);
            requireAuthor(report);
            checkpoint(attempt);
            var measured = upload(report, file);
            publish(attempt, measured);
        } catch (Failure failure) {
            finish(attempt, failure);
        } catch (StaleAttemptException lost) {
            log.warn("Generation attempt lost its lease and is abandoned: report_id={}", report.id());
            cleanup.removeObject(report.objectKey());
        } catch (SourceUnavailableException | AccessUnavailableException error) {
            finish(attempt, Failure.temporary(FailureCode.SOURCE_UNAVAILABLE, error));
        } catch (ObjectStorageUnavailableException error) {
            finish(attempt, Failure.temporary(FailureCode.STORAGE_UNAVAILABLE, error));
        } catch (SourceRejectedException rejected) {
            finish(attempt, Failure.permanent(rejected.reason()));
        } catch (ProjectNotFoundException gone) {
            finish(attempt, Failure.projectGone());
        } catch (ContentTooLargeException tooLarge) {
            finish(attempt, Failure.permanent(FailureCode.LIMIT_EXCEEDED));
        } catch (IOException | RuntimeException error) {
            log.error("Generation attempt failed unexpectedly: report_id={}", report.id(), error);
            finish(attempt, Failure.temporary(FailureCode.GENERATION_ERROR, error));
        } finally {
            if (file != null) deleteQuietly(file);
        }
    }

    /** Reads the sources from scratch for this attempt; nothing of an earlier attempt is reused. */
    private ReportAggregation collect(Attempt attempt) {
        var report = attempt.report;
        var parameters = report.generation();
        var period = parameters.period();
        List<MonitorInfo> monitors = new ArrayList<>();
        ProjectInfo project = null;
        String cursor = null;
        do {
            var page = sources.monitors(report.projectId(), parameters.selection(), cursor, limits.pageSize());
            project = page.project();
            monitors.addAll(page.monitors().items());
            if (monitors.size() > limits.maxMonitors()) throw Failure.permanent(FailureCode.LIMIT_EXCEEDED);
            cursor = page.monitors().hasNext() ? page.monitors().nextCursor() : null;
            checkpoint(attempt);
        } while (cursor != null);
        if (monitors.isEmpty()) throw Failure.permanent(FailureCode.NO_MONITORS);
        if (!parameters.selection().all() && monitors.size() != parameters.selection().size()) {
            throw Failure.permanent(FailureCode.MONITOR_NOT_IN_PROJECT);
        }

        var aggregation = new ReportAggregation(project, period, monitors);
        List<Long> ids = monitors.stream().map(MonitorInfo::id).toList();
        var rows = new long[1];
        var checks = walk(attempt, rows, next -> sources.checks(report.projectId(), ids, period, next,
                limits.pageSize()), aggregation::add);
        var incidents = walk(attempt, rows, next -> sources.incidents(report.projectId(), ids, period, next,
                limits.pageSize()), aggregation::add);
        var observed = checks.capturedAt() != null ? checks.capturedAt() : clock.instant();
        transactions.inTransaction(() -> {
            var current = reports.lockById(report.id()).orElseThrow(() -> lost(report));
            current.recordReadBounds(attempt.lease.token(),
                    new ReadBounds(observed, checks.upperId(), incidents.upperId()), clock.instant());
            reports.save(current);
        });
        return aggregation;
    }

    private <T> SourcePage<T> walk(Attempt attempt, long[] rows, Function<String, SourcePage<T>> read,
            Consumer<T> add) {
        SourcePage<T> first = null;
        SourcePage<T> page;
        String cursor = null;
        do {
            page = read.apply(cursor);
            if (first == null) first = page;
            rows[0] += page.items().size();
            if (rows[0] > limits.maxSourceRows()) throw Failure.permanent(FailureCode.LIMIT_EXCEEDED);
            page.items().forEach(add);
            cursor = page.hasNext() ? page.nextCursor() : null;
            checkpoint(attempt);
        } while (cursor != null);
        return first;
    }

    private void render(Report report, ReportAggregation aggregation, Path file) throws IOException {
        var renderer = renderers.get(report.format());
        var content = aggregation.content(report.generation().requestedAt(), clock.instant());
        try (OutputStream out = new LimitedOutputStream(Files.newOutputStream(file), maxFileSize)) {
            renderer.render(content, out);
        }
    }

    private MeasuredInputStream upload(Report report, Path file) throws IOException {
        long size = Files.size(file);
        try (var measured = new MeasuredInputStream(Files.newInputStream(file), maxFileSize)) {
            storage.put(report.objectKey(), measured, size, report.format().contentType());
            return measured;
        }
    }

    /** Only the current attempt of a report whose project still exists can make it READY. */
    private void publish(Attempt attempt, MeasuredInputStream measured) {
        var report = attempt.report;
        boolean ready = transactions.inTransaction(() -> {
            projects.lock(report.projectId());
            var current = reports.lockById(report.id()).orElseThrow(() -> lost(report));
            if (projects.deleted(report.projectId())) {
                current.markDeleting(clock.instant());
                reports.save(current);
                return false;
            }
            current.completeGeneration(attempt.lease.token(), measured.size(), measured.sha256(), clock.instant());
            reports.save(current);
            return true;
        });
        if (!ready) cleanup.purge(report);
    }

    /** Ends the attempt: the time limit, then the lease, which is renewed once half of it is used. */
    private void checkpoint(Attempt attempt) {
        var now = clock.instant();
        if (!now.isBefore(attempt.deadline)) throw Failure.temporary(FailureCode.TIMED_OUT, null);
        var remaining = Duration.between(now, attempt.lease.until());
        if (remaining.compareTo(limits.leaseDuration().dividedBy(2)) > 0) return;
        var renewed = now.plus(limits.leaseDuration());
        transactions.inTransaction(() -> {
            var current = reports.lockById(attempt.report.id()).orElseThrow(() -> lost(attempt.report));
            current.renewLease(attempt.lease.token(), renewed, now);
            reports.save(current);
        });
        attempt.lease = attempt.lease.extendedTo(renewed);
    }

    private void requireAuthor(Report report) {
        var decision = access.check(report.projectId(), report.createdBy(), AccessLevel.WRITE);
        if (decision == AccessDecision.PROJECT_NOT_FOUND) throw Failure.projectGone();
        if (decision == AccessDecision.DENIED) throw Failure.permanent(FailureCode.ACCESS_REVOKED);
    }

    private void finish(Attempt attempt, Failure failure) {
        var report = attempt.report;
        var token = attempt.lease.token();
        try {
            transactions.inTransaction(() -> {
                var current = reports.lockById(report.id()).orElseThrow(() -> lost(report));
                if (failure.projectGone) {
                    current.markDeleting(clock.instant());
                } else if (failure.temporary) {
                    current.retryLater(token, failure.code, limits.retryDelay(), limits.maxAttempts(), clock.instant());
                } else {
                    current.fail(token, failure.code, clock.instant());
                }
                reports.save(current);
            });
            log.info("Generation attempt ended without a report: report_id={}, reason={}, temporary={}",
                    report.id(), failure.code, failure.temporary, failure.getCause());
        } catch (StaleAttemptException lost) {
            log.warn("Generation attempt lost its lease before it could end: report_id={}", report.id());
        }
        if (failure.projectGone) cleanup.purge(report);
        else cleanup.removeObject(report.objectKey());
    }

    private static StaleAttemptException lost(Report report) {
        return new StaleAttemptException("Report " + report.id() + " no longer exists");
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException error) {
            log.warn("Temporary report file was not removed: file={}", file, error);
        }
    }

    private static final class Attempt {
        private final Report report;
        private final Instant deadline;
        private Lease lease;

        Attempt(Report report, Lease lease, Instant deadline) {
            this.report = report;
            this.lease = lease;
            this.deadline = deadline;
        }
    }

    /** How an attempt ends when it cannot produce a report. */
    private static final class Failure extends RuntimeException {
        private final FailureCode code;
        private final boolean temporary;
        private final boolean projectGone;

        private Failure(FailureCode code, boolean temporary, boolean projectGone, Throwable cause) {
            super(code == null ? "project deleted" : code.name(), cause, false, false);
            this.code = code;
            this.temporary = temporary;
            this.projectGone = projectGone;
        }

        static Failure temporary(FailureCode code, Throwable cause) {
            return new Failure(code, true, false, cause);
        }

        static Failure permanent(FailureCode code) {
            return new Failure(code, false, false, null);
        }

        static Failure projectGone() {
            return new Failure(null, false, true, null);
        }
    }
}
