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
                Fakes.TRANSACTIONS, clock, limits, maxFileSize);
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
}
