package org.awesoma.report.application.usecase;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.ReentrantLock;
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
import org.awesoma.report.application.port.ReportWorkspace;
import org.awesoma.report.application.port.ReportWorkspace.TemporaryReport;
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
 * Builds queued reports. An attempt claims a job with a lease, reads the sources page by page,
 * renders into a bounded temporary file, stores it under its own key and publishes it only while
 * it still holds the lease. Nothing of this runs inside a transaction. A temporary failure
 * requeues the job with a growing delay, a lasting one fails it; a partial report is never
 * published.
 */
public class GenerationWorker {
    private static final Logger log = LoggerFactory.getLogger(GenerationWorker.class);

    private final ReportRepository reports;
    private final ReportJobQueue queue;
    private final ReportDataSource sources;
    private final ProjectAccessPort access;
    private final ProjectGuard projects;
    private final ObjectStorage storage;
    private final ReportWorkspace workspace;
    private final Map<ReportFormat, ReportRenderer> renderers = new EnumMap<>(ReportFormat.class);
    private final ReportCleanup cleanup;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final GenerationLimits limits;
    private final long maxFileSize;

    public GenerationWorker(ReportRepository reports, ReportJobQueue queue, ReportDataSource sources,
            ProjectAccessPort access, ProjectGuard projects, ObjectStorage storage, List<ReportRenderer> renderers,
            ReportCleanup cleanup, TransactionRunner transactions, Clock clock, GenerationLimits limits,
            long maxFileSize, ReportWorkspace workspace) {
        this.reports = reports;
        this.queue = queue;
        this.sources = sources;
        this.access = access;
        this.projects = projects;
        this.storage = storage;
        this.workspace = workspace;
        renderers.forEach(renderer -> this.renderers.put(renderer.format(), renderer));
        this.cleanup = cleanup;
        this.transactions = transactions;
        this.clock = clock;
        this.limits = limits;
        this.maxFileSize = maxFileSize;
    }

    /** @return whether a job was claimed and worked on */
    public boolean runNext() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Report worker interrupted");
        }
        var now = clock.instant();
        var lease = new Lease(UUID.randomUUID(), UUID.randomUUID(), now.plus(limits.leaseDuration()));
        var claimed = queue.claimNext(lease, now);
        if (claimed.isEmpty()) return false;
        var attempt = new Attempt(claimed.get(), lease, now.plus(limits.attemptTimeout()));
        log.info("Report generation started: report_id={} project_id={} format={} attempt={}",
                attempt.report.id(), attempt.report.projectId(), attempt.report.format(), attempt.report.attempts());
        runIsolated(attempt);
        return true;
    }

    /** On its own thread, so that the heartbeat's interrupt never reaches the scheduler's thread. */
    private void runIsolated(Attempt attempt) {
        var task = new FutureTask<Void>(() -> {
            run(attempt);
            return null;
        });
        Thread thread = Thread.ofVirtual().name("report-attempt-" + attempt.report.id()).start(task);
        try {
            task.get();
        } catch (InterruptedException interrupted) {
            attempt.locked(() -> attempt.cancelled = true);
            task.cancel(true);
            // FutureTask.cancel marks the future done before its body unwinds. Keep the scheduler
            // slot until the attempt has really stopped and cleaned its heartbeat and temporary file.
            joinUninterruptibly(thread);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Report worker interrupted", interrupted);
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failed.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("Report worker failed", failed.getCause());
        }
    }

    private static void joinUninterruptibly(Thread thread) {
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException again) {
                thread.interrupt();
            }
        }
    }

    /** Jobs whose worker stopped renewing its lease go back to the queue, or fail if out of attempts. */
    public int recoverExpired(int limit) {
        int recovered = 0;
        for (UUID id : queue.expiredUploads(clock.instant(), limit)) {
            String abandoned = transactions.inTransaction(() -> reports.lockById(id)
                    .filter(current -> current.status() == ReportStatus.UPLOADING && current.lease() != null
                            && !current.lease().heldAt(clock.instant()))
                    .map(current -> {
                        String key = current.objectKey();
                        current.failUpload(current.lease().token(), clock.instant());
                        reports.save(current);
                        return key;
                    }).orElse(null));
            if (abandoned != null) {
                cleanup.removeObject(abandoned);
                recovered++;
            }
        }
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
        try (var heartbeat = new Heartbeat(attempt)) {
            requireAuthor(report);
            var aggregation = collect(attempt);
            log.info("Report source data collected: report_id={} project_id={} monitors={}",
                    report.id(), report.projectId(), aggregation.monitorCount());
            try (var file = workspace.create(report.id(), report.format())) {
                render(report, aggregation, file);
                log.debug("Report rendering completed: report_id={} format={}", report.id(), report.format());
                requireAuthor(report);
                checkpoint(attempt);
                var measured = upload(report, file);
                log.debug("Report object uploaded: report_id={} project_id={} bytes={}",
                        report.id(), report.projectId(), measured.size());
                checkpoint(attempt);
                requireAuthor(report);
                publish(attempt, measured);
            }
        } catch (IOException | RuntimeException error) {
            Throwable cause = attempt.heartbeatFailure != null ? attempt.heartbeatFailure : error;
            if (attempt.heartbeatFailure != null) Thread.interrupted();
            end(attempt, cause);
        }
    }

    private void end(Attempt attempt, Throwable cause) {
        Report report = attempt.report;
        switch (cause) {
            case Failure failure -> finish(attempt, failure);
            case StaleAttemptException stale -> {
                log.warn("Generation attempt lost its lease and is abandoned: report_id={}", report.id());
                cleanup.removeUnreferenced(report);
            }
            case SourceUnavailableException unavailable ->
                    finish(attempt, Failure.temporary(FailureCode.SOURCE_UNAVAILABLE, cause));
            case AccessUnavailableException unavailable ->
                    finish(attempt, Failure.temporary(FailureCode.SOURCE_UNAVAILABLE, cause));
            case ObjectStorageUnavailableException unavailable ->
                    finish(attempt, Failure.temporary(FailureCode.STORAGE_UNAVAILABLE, cause));
            case SourceRejectedException rejected -> finish(attempt, Failure.permanent(rejected.reason()));
            case ProjectNotFoundException gone -> finish(attempt, Failure.projectGone());
            case ContentTooLargeException tooLarge -> finish(attempt, Failure.permanent(FailureCode.LIMIT_EXCEEDED));
            default -> {
                log.error("Generation attempt failed unexpectedly: report_id={}", report.id(), cause);
                finish(attempt, Failure.temporary(FailureCode.GENERATION_ERROR, cause));
            }
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
        var budget = new RowBudget(limits.maxSourceRows());
        var checks = walk(attempt, budget, next -> sources.checks(report.projectId(), ids, period, next,
                limits.pageSize()), aggregation::add);
        var incidents = walk(attempt, budget, next -> sources.incidents(report.projectId(), ids, period, next,
                limits.pageSize()), aggregation::add);
        var observed = checks.capturedAt() != null ? checks.capturedAt() : clock.instant();
        transactions.inTransaction(() -> {
            var current = reports.lockById(report.id()).orElseThrow(() -> lost(report));
            if (!clock.instant().isBefore(attempt.deadline)) throw Failure.temporary(FailureCode.TIMED_OUT, null);
            current.recordReadBounds(attempt.lease.token(),
                    new ReadBounds(observed, checks.upperId(), incidents.upperId()), clock.instant());
            reports.save(current);
        });
        return aggregation;
    }

    /** Reads every page of one source; returns the first, which carries the bounds of the walk. */
    private <T> SourcePage<T> walk(Attempt attempt, RowBudget budget, Function<String, SourcePage<T>> read,
            Consumer<T> add) {
        SourcePage<T> first = null;
        String cursor = null;
        do {
            var page = read.apply(cursor);
            if (first == null) first = page;
            budget.take(page.items().size());
            page.items().forEach(add);
            cursor = page.hasNext() ? page.nextCursor() : null;
            checkpoint(attempt);
        } while (cursor != null);
        return first;
    }

    private void render(Report report, ReportAggregation aggregation, TemporaryReport file) throws IOException {
        var renderer = renderers.get(report.format());
        var content = aggregation.content(report.generation().requestedAt(), clock.instant());
        try (OutputStream out = new LimitedOutputStream(file.write(), maxFileSize)) {
            renderer.render(content, out);
        }
    }

    private MeasuredInputStream upload(Report report, TemporaryReport file) throws IOException {
        long size = file.size();
        try (var measured = new MeasuredInputStream(file.read(), maxFileSize)) {
            storage.put(report.objectKey(), measured, size, report.format().contentType());
            return measured;
        }
    }

    /** Only the current attempt of a report whose project still exists can make it READY. */
    private void publish(Attempt attempt, MeasuredInputStream measured) {
        var report = attempt.report;
        boolean ready;
        attempt.lock.lock();
        try {
            if (attempt.cancelled) throw lost(report);
            if (attempt.heartbeatFailure != null) throw attempt.heartbeatFailure;
            ready = transactions.inTransaction(() -> {
                projects.lock(report.projectId());
                var current = reports.lockById(report.id()).orElseThrow(() -> lost(report));
                if (!clock.instant().isBefore(attempt.deadline)) throw Failure.temporary(FailureCode.TIMED_OUT, null);
                if (projects.deleted(report.projectId())) {
                    current.markDeleting(clock.instant());
                    reports.save(current);
                    return false;
                }
                current.completeGeneration(attempt.lease.token(), measured.size(), measured.sha256(), clock.instant());
                reports.save(current);
                return true;
            });
            attempt.completed = true;
        } finally {
            attempt.lock.unlock();
        }
        if (!ready) cleanup.purge(report);
        else log.info("Report generation completed: report_id={} project_id={} format={} bytes={}",
                report.id(), report.projectId(), report.format(), measured.size());
    }

    /** Ends the attempt: the time limit, then the lease, which is renewed once half of it is used. */
    private void checkpoint(Attempt attempt) {
        attempt.locked(() -> {
            if (attempt.cancelled) throw lost(attempt.report);
            if (attempt.closed || attempt.completed) return;
            if (attempt.heartbeatFailure != null) throw attempt.heartbeatFailure;
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
        });
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
        else cleanup.removeUnreferenced(report);
    }

    private static StaleAttemptException lost(Report report) {
        return new StaleAttemptException("Report " + report.id() + " no longer exists");
    }

    /** Rows of checks and incidents together that one attempt may read. */
    private static final class RowBudget {
        private long left;

        RowBudget(long limit) {
            this.left = limit;
        }

        void take(int rows) {
            left -= rows;
            if (left < 0) throw Failure.permanent(FailureCode.LIMIT_EXCEEDED);
        }
    }

    private static final class Attempt {
        /** A lock rather than a monitor: on Java 21 a monitor held during JDBC pins the virtual thread. */
        private final ReentrantLock lock = new ReentrantLock();
        private final Report report;
        private final Instant deadline;
        private volatile Lease lease;
        private volatile RuntimeException heartbeatFailure;
        private boolean closed;
        private boolean completed;
        private boolean cancelled;

        Attempt(Report report, Lease lease, Instant deadline) {
            this.report = report;
            this.lease = lease;
            this.deadline = deadline;
        }

        void locked(Runnable action) {
            lock.lock();
            try {
                action.run();
            } finally {
                lock.unlock();
            }
        }
    }

    /** Keeps the lease alive even while a source, renderer or object upload is blocked. */
    private final class Heartbeat implements AutoCloseable {
        private final Attempt attempt;
        private final Thread owner = Thread.currentThread();
        private final Thread thread;

        Heartbeat(Attempt attempt) {
            this.attempt = attempt;
            long interval = Math.max(1, Math.min(limits.leaseDuration().toMillis() / 3,
                    limits.attemptTimeout().toMillis() / 3));
            thread = Thread.ofVirtual().name("report-lease-" + attempt.report.id()).start(() -> beat(interval));
        }

        private void beat(long interval) {
            try {
                while (true) {
                    Thread.sleep(interval);
                    checkpoint(attempt);
                }
            } catch (InterruptedException stopped) {
                // The attempt has ended.
            } catch (RuntimeException failure) {
                attempt.locked(() -> {
                    if (!attempt.closed && !attempt.completed) {
                        attempt.heartbeatFailure = failure;
                        owner.interrupt();
                    }
                });
            }
        }

        @Override
        public void close() {
            attempt.locked(() -> {
                attempt.closed = true;
                thread.interrupt();
            });
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
