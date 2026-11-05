package org.awesoma.report.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.awesoma.report.application.port.AccessDecision;
import org.awesoma.report.application.port.ReportRenderer;
import org.awesoma.report.application.port.SourceRejectedException;
import org.awesoma.report.application.port.SourceUnavailableException;
import org.awesoma.report.domain.FailureCode;
import org.awesoma.report.domain.GenerationParameters;
import org.awesoma.report.domain.Lease;
import org.awesoma.report.domain.MonitorSelection;
import org.awesoma.report.domain.ObjectKeys;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportFormat;
import org.awesoma.report.domain.ReportPeriod;
import org.awesoma.report.domain.ReportStatus;
import org.awesoma.report.domain.FileName;
import org.awesoma.report.application.port.TransactionRunner;
import org.awesoma.report.domain.content.ReportContent;
import org.awesoma.report.domain.source.CheckOutcome;
import org.awesoma.report.domain.source.CheckRecord;
import org.awesoma.report.domain.source.IncidentRecord;
import org.awesoma.report.domain.source.IncidentSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenerationWorkerTest {
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final ReportPeriod PERIOD = new ReportPeriod(Instant.parse("2026-10-01T00:00:00Z"),
            Instant.parse("2026-10-02T00:00:00Z"));

    private final Fakes.Reports reports = new Fakes.Reports();
    private final Fakes.Storage storage = new Fakes.Storage();
    private final Fakes.Keys keys = new Fakes.Keys();
    private final Fakes.Sources sources = new Fakes.Sources();
    private final Fakes.Access access = new Fakes.Access();
    private final Fakes.Projects projects = new Fakes.Projects();
    private final MutableClock clock = new MutableClock(NOW);
    private final List<ReportContent> rendered = new ArrayList<>();
    private GenerationLimits limits = GenerationServiceTest.LIMITS;
    private long maxFileSize = 10 * 1024 * 1024;

    @BeforeEach
    void data() {
        for (int i = 1; i <= 120; i++) {
            sources.checks.add(new CheckRecord(i, i % 2 == 0 ? 11 : 12, i % 10 == 0 ? CheckOutcome.TIMEOUT
                    : CheckOutcome.SUCCESS, i % 10 == 0 ? null : (long) i, PERIOD.from().plusSeconds(i)));
        }
        sources.incidents.add(new IncidentRecord(1, 11, IncidentSeverity.HIGH, PERIOD.from().minusSeconds(60), null));
    }

    private GenerationWorker worker() {
        var cleanup = new ReportCleanup(reports, storage, keys, Fakes.TRANSACTIONS, clock, Duration.ofHours(24));
        ReportRenderer renderer = new ReportRenderer() {
            @Override
            public ReportFormat format() {
                return ReportFormat.CSV;
            }

            @Override
            public void render(ReportContent content, OutputStream out) throws java.io.IOException {
                rendered.add(content);
                out.write(("rows=" + content.rows().size()).getBytes(StandardCharsets.UTF_8));
            }
        };
        return new GenerationWorker(reports, reports, sources, access, projects, storage, List.of(renderer), cleanup,
                Fakes.TRANSACTIONS, clock, limits, maxFileSize, new TestWorkspace());
    }

    private Report queue(MonitorSelection selection) {
        return reports.save(Report.queueGeneration(UUID.randomUUID(), 42, 7, ReportFormat.CSV,
                new GenerationParameters(PERIOD, selection, NOW)));
    }

    @Test
    void buildsTheReportPageByPageAndPublishesIt() {
        var job = queue(MonitorSelection.allMonitors());

        assertThat(worker().runNext()).isTrue();

        var report = reports.row(job.id());
        assertThat(report.status()).isEqualTo(ReportStatus.READY);
        assertThat(report.attempts()).isEqualTo(1);
        assertThat(report.generatedAt()).isEqualTo(NOW);
        assertThat(report.readBounds().checksUpperId()).isEqualTo(120L);
        assertThat(report.readBounds().incidentsUpperId()).isEqualTo(1L);
        assertThat(report.objectKey()).startsWith(ObjectKeys.upload(42, job.id()) + "/attempts/");
        assertThat(storage.objects.get(report.objectKey())).asString(StandardCharsets.UTF_8).isEqualTo("rows=2");
        assertThat(sources.calls.get("checks")).isEqualTo(3);
        var rows = rendered.get(0).rows();
        assertThat(rows.get(0).checks().total()).isEqualTo(60);
        assertThat(rows.get(0).incidents().openAtEnd()).isEqualTo(1);
        assertThat(rows.get(1).checks().timeout()).isZero();
        assertThat(worker().runNext()).isFalse();
    }

    @Test
    void aFailedPageRestartsTheAttemptFromScratchWithoutCountingTwice() {
        var job = queue(MonitorSelection.allMonitors());
        sources.failOnCall.put("checks#2", () -> new SourceUnavailableException("down", null));

        worker().runNext();
        var waiting = reports.row(job.id());
        assertThat(waiting.status()).isEqualTo(ReportStatus.QUEUED);
        assertThat(waiting.nextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(worker().runNext()).as("not due yet").isFalse();

        clock.advance(Duration.ofSeconds(30));
        worker().runNext();

        assertThat(reports.row(job.id()).status()).isEqualTo(ReportStatus.READY);
        assertThat(reports.row(job.id()).attempts()).isEqualTo(2);
        assertThat(rendered).hasSize(1);
        assertThat(rendered.get(0).rows()).extracting(row -> row.checks().total()).containsExactly(60L, 60L);
        assertThat(storage.objects).hasSize(1);
    }

    @Test
    void failsAfterTheLastAttempt() {
        var job = queue(MonitorSelection.allMonitors());
        for (int call = 1; call <= 3; call++) {
            sources.failOnCall.put("monitors#" + call, () -> new SourceUnavailableException("down", null));
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            worker().runNext();
            clock.advance(Duration.ofMinutes(5));
        }

        var report = reports.row(job.id());
        assertThat(report.status()).isEqualTo(ReportStatus.FAILED);
        assertThat(report.failure()).isEqualTo(FailureCode.SOURCE_UNAVAILABLE);
        assertThat(report.attempts()).isEqualTo(3);
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void neverPublishesAReportCutAtALimit() {
        limits = new GenerationLimits(Duration.ofDays(31), 100, 100, 50, Duration.ofMinutes(5), 3,
                Duration.ofSeconds(30), Duration.ofMinutes(1));
        var rows = queue(MonitorSelection.allMonitors());
        worker().runNext();
        assertThat(reports.row(rows.id()).failure()).isEqualTo(FailureCode.LIMIT_EXCEEDED);

        limits = GenerationServiceTest.LIMITS;
        maxFileSize = 3;
        var size = queue(MonitorSelection.allMonitors());
        worker().runNext();
        assertThat(reports.row(size.id()).status()).isEqualTo(ReportStatus.FAILED);
        assertThat(reports.row(size.id()).failure()).isEqualTo(FailureCode.LIMIT_EXCEEDED);
        assertThat(storage.objects).isEmpty();
        assertThat(rendered).hasSize(1);
    }

    @Test
    void endsWhenTheSelectionCannotBeMet() {
        sources.monitors.clear();
        var none = queue(MonitorSelection.allMonitors());
        worker().runNext();
        assertThat(reports.row(none.id()).failure()).isEqualTo(FailureCode.NO_MONITORS);

        sources.failOnCall.put("monitors#2", () -> new SourceRejectedException(FailureCode.MONITOR_NOT_IN_PROJECT, "x"));
        var foreign = queue(MonitorSelection.requested(List.of(11L)));
        worker().runNext();
        assertThat(reports.row(foreign.id()).failure()).isEqualTo(FailureCode.MONITOR_NOT_IN_PROJECT);
    }

    @Test
    void aRevokedRightFailsTheJobAndAGoneProjectDeletesIt() {
        access.decision = AccessDecision.DENIED;
        var revoked = queue(MonitorSelection.allMonitors());
        worker().runNext();
        assertThat(reports.row(revoked.id()).failure()).isEqualTo(FailureCode.ACCESS_REVOKED);

        access.decision = AccessDecision.PROJECT_NOT_FOUND;
        var gone = queue(MonitorSelection.allMonitors());
        worker().runNext();
        assertThat(reports.rows).doesNotContainKey(gone.id());
    }

    @Test
    void aProjectDeletedBeforePublicationGetsNoReport() {
        var job = queue(MonitorSelection.allMonitors());
        storage.onPut = () -> projects.markDeleted(42);

        worker().runNext();

        assertThat(reports.rows).doesNotContainKey(job.id());
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void aJobDeletedWhileItRunsIsNotRevived() {
        var job = queue(MonitorSelection.allMonitors());
        storage.onPut = () -> reports.row(job.id()).markDeleting(clock.instant());

        worker().runNext();

        assertThat(reports.row(job.id()).status()).isEqualTo(ReportStatus.DELETING);
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void anAttemptOverItsTimeLimitIsRetried() {
        var job = queue(MonitorSelection.allMonitors());
        sources.failOnCall.put("checks#1", () -> {
            clock.advance(Duration.ofMinutes(6));
            return null;
        });

        worker().runNext();

        assertThat(reports.row(job.id()).status()).isEqualTo(ReportStatus.QUEUED);
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void renewsTheLeaseWhileWorking() {
        var job = queue(MonitorSelection.allMonitors());
        for (int call = 1; call <= 3; call++) {
            sources.failOnCall.put("checks#" + call, () -> {
                clock.advance(Duration.ofSeconds(40));
                return null;
            });
        }

        worker().runNext();

        assertThat(reports.row(job.id()).status()).isEqualTo(ReportStatus.READY);
        assertThat(reports.row(job.id()).generatedAt()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void recoversAJobWhoseWorkerStopped() {
        var job = queue(MonitorSelection.allMonitors());
        var lease = new Lease(UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(60));
        var claimed = reports.claimNext(lease, NOW).orElseThrow();
        storage.objects.put(claimed.objectKey(), new byte[] {1});

        assertThat(worker().recoverExpired(10)).isZero();
        clock.advance(Duration.ofSeconds(61));
        assertThat(worker().recoverExpired(10)).isEqualTo(1);

        assertThat(reports.row(job.id()).status()).isEqualTo(ReportStatus.QUEUED);
        assertThat(storage.objects).isEmpty();
        clock.advance(Duration.ofSeconds(30));
        worker().runNext();
        assertThat(reports.row(job.id()).status()).isEqualTo(ReportStatus.READY);
    }

    @Test
    void recoversOnlyExpiredUploadsAndReconciliationRemovesLateWrites() {
        var expiredLease = new Lease(UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(10));
        var activeLease = new Lease(UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(120));
        var expired = reports.save(Report.startUpload(UUID.randomUUID(), 42, 7, FileName.fromClient("old.csv"),
                expiredLease, NOW));
        var active = reports.save(Report.startUpload(UUID.randomUUID(), 42, 7, FileName.fromClient("active.csv"),
                activeLease, NOW));
        var withoutObject = reports.save(Report.startUpload(UUID.randomUUID(), 42, 7,
                FileName.fromClient("missing.csv"), expiredLease, NOW));
        storage.objects.put(expired.objectKey(), new byte[] {1});
        clock.advance(Duration.ofSeconds(11));
        assertThat(worker().recoverExpired(10)).isEqualTo(2);
        assertThat(reports.row(expired.id()).status()).isEqualTo(ReportStatus.FAILED);
        assertThat(reports.row(withoutObject.id()).status()).isEqualTo(ReportStatus.FAILED);
        assertThat(reports.row(active.id()).status()).isEqualTo(ReportStatus.UPLOADING);
        assertThat(storage.objects).doesNotContainKey(expired.objectKey());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> reports.row(expired.id())
                .completeUpload(expiredLease.token(), 1, "a".repeat(64), clock.instant()))
                .isInstanceOf(org.awesoma.report.domain.StaleAttemptException.class);
        storage.objects.put(expired.objectKey(), new byte[] {1});
        storage.written.put(expired.objectKey(), NOW);
        var cleanup = new ReportCleanup(reports, storage, keys, Fakes.TRANSACTIONS, clock, Duration.ofHours(24));
        var maintenance = new ReportMaintenance(reports, storage, keys, cleanup, clock, Duration.ofSeconds(1));
        assertThat(maintenance.reconcile()).isEqualTo(1);
        assertThat(storage.objects).doesNotContainKey(expired.objectKey());
    }

    @Test
    void doesNotDeletePublishedObjectWhenCommitResultIsAmbiguous() {
        var job = queue(MonitorSelection.allMonitors());
        var failedOnce = new java.util.concurrent.atomic.AtomicBoolean();
        TransactionRunner ambiguous = new TransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> work) {
                T result = work.get();
                if (reports.row(job.id()).status() == ReportStatus.READY && failedOnce.compareAndSet(false, true))
                    throw new IllegalStateException("commit acknowledged too late");
                return result;
            }
        };
        var cleanup = new ReportCleanup(reports, storage, keys, ambiguous, clock, Duration.ofHours(24));
        ReportRenderer renderer = new ReportRenderer() {
            public ReportFormat format() { return ReportFormat.CSV; }
            public void render(ReportContent content, OutputStream out) throws java.io.IOException { out.write(1); }
        };
        var worker = new GenerationWorker(reports, reports, sources, access, projects, storage, List.of(renderer),
                cleanup, ambiguous, clock, limits, maxFileSize, new TestWorkspace());
        worker.runNext();
        var ready = reports.row(job.id());
        assertThat(ready.status()).isEqualTo(ReportStatus.READY);
        assertThat(storage.objects).containsKey(ready.objectKey());
        assertThat(worker.runNext()).isFalse();
    }

    @Test
    void cannotPublishWhenTheUploadCrossesTheAttemptDeadline() {
        var job = queue(MonitorSelection.allMonitors());
        storage.onPut = () -> clock.advance(Duration.ofMinutes(6));
        worker().runNext();
        assertThat(reports.row(job.id()).status()).isEqualTo(ReportStatus.QUEUED);
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void heartbeatRenewsWhileRendererIsBlockedAndDoesNotInterruptTheNextJob() throws Exception {
        var job = queue(MonitorSelection.allMonitors());
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var realClock = java.time.Clock.systemUTC();
        var shortLease = new GenerationLimits(Duration.ofDays(31), 100, 100_000, 50, Duration.ofSeconds(5), 3,
                Duration.ofSeconds(1), Duration.ofMillis(600));
        var cleanup = new ReportCleanup(reports, storage, keys, Fakes.TRANSACTIONS, realClock, Duration.ofHours(24));
        ReportRenderer renderer = new ReportRenderer() {
            public ReportFormat format() { return ReportFormat.CSV; }
            public void render(ReportContent content, OutputStream out) throws java.io.IOException {
                entered.countDown();
                try { if (!release.await(3, java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("timeout"); }
                catch (InterruptedException error) { throw new java.io.IOException(error); }
                out.write(1);
            }
        };
        var worker = new GenerationWorker(reports, reports, sources, access, projects, storage, List.of(renderer),
                cleanup, Fakes.TRANSACTIONS, realClock, shortLease, maxFileSize, new TestWorkspace());
        var result = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            worker.runNext();
            boolean interrupted = Thread.currentThread().isInterrupted();
            worker.runNext();
            return interrupted || Thread.currentThread().isInterrupted();
        });
        assertThat(entered.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        Instant original = reports.findById(job.id()).orElseThrow().lease().until();
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(reports.findById(job.id()).orElseThrow().lease().until()).isAfter(original));
        release.countDown();
        assertThat(result.get(3, java.util.concurrent.TimeUnit.SECONDS)).isFalse();
        assertThat(reports.row(job.id()).status()).isEqualTo(ReportStatus.READY);
    }

    @Test
    void deadlineInterruptsABlockedRendererAndRetriesTheJob() throws Exception {
        var job = queue(MonitorSelection.allMonitors());
        var entered = new java.util.concurrent.CountDownLatch(1);
        var realClock = java.time.Clock.systemUTC();
        var shortTimeout = new GenerationLimits(Duration.ofDays(31), 100, 100_000, 50, Duration.ofMillis(600), 3,
                Duration.ofSeconds(1), Duration.ofSeconds(3));
        var cleanup = new ReportCleanup(reports, storage, keys, Fakes.TRANSACTIONS, realClock, Duration.ofHours(24));
        ReportRenderer renderer = new ReportRenderer() {
            public ReportFormat format() { return ReportFormat.CSV; }
            public void render(ReportContent content, OutputStream out) throws java.io.IOException {
                entered.countDown();
                try { new java.util.concurrent.CountDownLatch(1).await(); }
                catch (InterruptedException error) { throw new java.io.IOException(error); }
            }
        };
        var worker = new GenerationWorker(reports, reports, sources, access, projects, storage, List.of(renderer),
                cleanup, Fakes.TRANSACTIONS, realClock, shortTimeout, maxFileSize, new TestWorkspace());
        var result = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            worker.runNext();
            return Thread.currentThread().isInterrupted();
        });
        assertThat(entered.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(result.get(3, java.util.concurrent.TimeUnit.SECONDS)).isFalse();
        assertThat(reports.row(job.id()).status()).isEqualTo(ReportStatus.QUEUED);
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void deadlineInterruptsABlockedObjectUploadAndRetriesTheJob() throws Exception {
        var job = queue(MonitorSelection.allMonitors());
        var entered = new java.util.concurrent.CountDownLatch(1);
        var realClock = java.time.Clock.systemUTC();
        var shortTimeout = new GenerationLimits(Duration.ofDays(31), 100, 100_000, 50, Duration.ofMillis(600), 3,
                Duration.ofSeconds(1), Duration.ofSeconds(3));
        org.awesoma.report.application.port.ObjectStorage blocking = new org.awesoma.report.application.port.ObjectStorage() {
            public void put(String key, java.io.InputStream content, long size, String type) {
                entered.countDown();
                try { new java.util.concurrent.CountDownLatch(1).await(); }
                catch (InterruptedException error) {
                    throw new org.awesoma.report.application.port.ObjectStorageUnavailableException("interrupted", error);
                }
            }
            public org.awesoma.report.application.port.StoredObject open(String key) { return storage.open(key); }
            public void delete(String key) { storage.delete(key); }
            public boolean exists(String key) { return storage.exists(key); }
            public java.util.List<org.awesoma.report.application.port.StoredKey> list(String prefix) { return storage.list(prefix); }
        };
        var cleanup = new ReportCleanup(reports, blocking, keys, Fakes.TRANSACTIONS, realClock, Duration.ofHours(24));
        ReportRenderer renderer = new ReportRenderer() {
            public ReportFormat format() { return ReportFormat.CSV; }
            public void render(ReportContent content, OutputStream out) throws java.io.IOException { out.write(1); }
        };
        var worker = new GenerationWorker(reports, reports, sources, access, projects, blocking, List.of(renderer),
                cleanup, Fakes.TRANSACTIONS, realClock, shortTimeout, maxFileSize, new TestWorkspace());
        var result = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            worker.runNext();
            return Thread.currentThread().isInterrupted();
        });
        assertThat(entered.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(result.get(3, java.util.concurrent.TimeUnit.SECONDS)).isFalse();
        assertThat(reports.row(job.id()).status()).isEqualTo(ReportStatus.QUEUED);
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void schedulerShutdownPreservesItsInterruptAndFencesAnUncooperativeAttempt() throws Exception {
        var job = queue(MonitorSelection.allMonitors());
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var finished = new java.util.concurrent.CountDownLatch(1);
        var interrupted = new java.util.concurrent.CountDownLatch(1);
        var schedulerInterrupted = new java.util.concurrent.atomic.AtomicBoolean();
        var realClock = java.time.Clock.systemUTC();
        var shortLease = new GenerationLimits(Duration.ofDays(31), 100, 100_000, 50, Duration.ofSeconds(5), 3,
                Duration.ofSeconds(1), Duration.ofMillis(600));
        var cleanup = new ReportCleanup(reports, storage, keys, Fakes.TRANSACTIONS, realClock, Duration.ofHours(24));
        ReportRenderer renderer = new ReportRenderer() {
            public ReportFormat format() { return ReportFormat.CSV; }
            public void render(ReportContent content, OutputStream out) throws java.io.IOException {
                entered.countDown();
                while (true) {
                    try { release.await(); break; }
                    catch (InterruptedException ignored) { interrupted.countDown(); /* Simulate a slow unwind. */ }
                }
                out.write(1);
                finished.countDown();
            }
        };
        var worker = new GenerationWorker(reports, reports, sources, access, projects, storage, List.of(renderer),
                cleanup, Fakes.TRANSACTIONS, realClock, shortLease, maxFileSize, new TestWorkspace());
        var scheduler = Thread.ofPlatform().start(() -> {
            try { worker.runNext(); }
            catch (IllegalStateException expected) {
                schedulerInterrupted.set(Thread.currentThread().isInterrupted());
                org.assertj.core.api.Assertions.assertThatThrownBy(worker::runNext).isInstanceOf(IllegalStateException.class);
            }
        });
        try {
            assertThat(entered.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            scheduler.interrupt();
            assertThat(interrupted.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(scheduler.isAlive()).isTrue();
            release.countDown();
            scheduler.join(3000);
            assertThat(scheduler.isAlive()).isFalse();
            assertThat(schedulerInterrupted).isTrue();
        } finally {
            release.countDown();
        }
        assertThat(finished.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(4)).untilAsserted(() -> {
            worker.recoverExpired(10);
            assertThat(reports.row(job.id()).status()).isEqualTo(ReportStatus.QUEUED);
        });
        assertThat(storage.objects).isEmpty();
    }
    private static final class TestWorkspace implements org.awesoma.report.application.port.ReportWorkspace {
        @Override
        public TemporaryReport create(UUID id, ReportFormat format) {
            return new TemporaryReport() {
                private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                public java.io.OutputStream write() { return bytes; }
                public java.io.InputStream read() { return new java.io.ByteArrayInputStream(bytes.toByteArray()); }
                public long size() { return bytes.size(); }
                public void close() { }
            };
        }
    }
}
