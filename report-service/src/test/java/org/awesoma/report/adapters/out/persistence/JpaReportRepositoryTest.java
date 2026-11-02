package org.awesoma.report.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.awesoma.report.application.port.ReportQuery;
import org.awesoma.report.application.port.ReportRepository;
import org.awesoma.report.domain.FailureCode;
import org.awesoma.report.domain.FileName;
import org.awesoma.report.domain.GenerationParameters;
import org.awesoma.report.domain.Lease;
import org.awesoma.report.domain.MonitorSelection;
import org.awesoma.report.domain.ReadBounds;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportFormat;
import org.awesoma.report.domain.ReportPeriod;
import org.awesoma.report.domain.ReportSource;
import org.awesoma.report.domain.ReportStatus;
import org.awesoma.report.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class JpaReportRepositoryTest extends AbstractIntegrationTest {
    private static final AtomicLong PROJECTS = new AtomicLong(1000);
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final String HASH = "b".repeat(64);

    @Autowired private ReportRepository reports;
    @Autowired private ReportJpaRepository rows;
    @Autowired private TransactionTemplate transactions;

    @Test
    void savesAndLoadsAnUploadThroughItsLifecycle() {
        long project = PROJECTS.incrementAndGet();
        var lease = new Lease(UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(60));
        var report = Report.startUpload(UUID.randomUUID(), project, 7, FileName.fromClient("Отчёт.pdf"), lease, NOW);

        reports.save(report);
        assertThat(reports.findById(report.id()).orElseThrow().snapshot()).isEqualTo(report.snapshot());

        report.completeUpload(lease.token(), 4096, HASH, NOW.plusSeconds(5));
        reports.save(report);

        var loaded = reports.findById(report.id()).orElseThrow();
        assertThat(loaded.snapshot()).isEqualTo(report.snapshot());
        assertThat(loaded.status()).isEqualTo(ReportStatus.READY);
        assertThat(loaded.fileName().value()).isEqualTo("Отчёт.pdf");
    }

    @Test
    void savesEveryGenerationField() {
        long project = PROJECTS.incrementAndGet();
        var period = new ReportPeriod(NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(1)));
        var report = Report.queueGeneration(UUID.randomUUID(), project, 7, ReportFormat.CSV,
                new GenerationParameters(period, MonitorSelection.requested(List.of(12L, 11L)), NOW));
        var lease = new Lease(UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(60));
        report.startAttempt(lease, NOW);
        report.recordReadBounds(lease.token(), new ReadBounds(NOW.plusSeconds(1), 900L, null), NOW.plusSeconds(1));
        report.retryLater(lease.token(), FailureCode.SOURCE_UNAVAILABLE, Duration.ofSeconds(30), 3,
                NOW.plusSeconds(2));

        reports.save(report);

        var loaded = reports.findById(report.id()).orElseThrow();
        assertThat(loaded.snapshot()).isEqualTo(report.snapshot());
        assertThat(loaded.generation().selection().monitorIds()).containsExactly(11L, 12L);
        assertThat(loaded.nextAttemptAt()).isEqualTo(NOW.plusSeconds(32));
        assertThat(loaded.readBounds().checksUpperId()).isEqualTo(900L);
    }

    @Test
    void keepsTheAllMonitorsSelection() {
        var report = queued(PROJECTS.incrementAndGet(), NOW);

        reports.save(report);

        assertThat(reports.findById(report.id()).orElseThrow().generation().selection().all()).isTrue();
    }

    @Test
    void listsVisibleReportsNewestFirstWithFiltersAndPages() {
        long project = PROJECTS.incrementAndGet();
        var oldest = reports.save(queued(project, NOW.minusSeconds(30)));
        var sameTimeA = reports.save(queued(project, NOW.minusSeconds(10)));
        var sameTimeB = reports.save(queued(project, NOW.minusSeconds(10)));
        var upload = reports.save(ready(project, NOW));
        var deleted = queued(project, NOW.plusSeconds(1));
        deleted.markDeleting(NOW.plusSeconds(2));
        reports.save(deleted);
        reports.save(queued(PROJECTS.incrementAndGet(), NOW));

        var all = reports.findVisible(new ReportQuery(project, null, null, 0, 50));
        // Postgres orders UUIDs by their bytes, as their text does; UUID.compareTo uses signed longs.
        UUID tieFirst = sameTimeA.id().toString().compareTo(sameTimeB.id().toString()) > 0
                ? sameTimeA.id() : sameTimeB.id();
        UUID tieSecond = tieFirst.equals(sameTimeA.id()) ? sameTimeB.id() : sameTimeA.id();
        assertThat(all.items()).extracting(Report::id)
                .containsExactly(upload.id(), tieFirst, tieSecond, oldest.id());
        assertThat(all.total()).isEqualTo(4);
        assertThat(all.hasNext()).isFalse();

        var firstPage = reports.findVisible(new ReportQuery(project, null, null, 0, 3));
        var secondPage = reports.findVisible(new ReportQuery(project, null, null, 1, 3));
        assertThat(firstPage.items()).hasSize(3);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.items()).extracting(Report::id).containsExactly(oldest.id());

        assertThat(reports.findVisible(new ReportQuery(project, null, ReportSource.UPLOADED, 0, 50)).items())
                .extracting(Report::id).containsExactly(upload.id());
        assertThat(reports.findVisible(new ReportQuery(project, ReportStatus.QUEUED, ReportSource.GENERATED, 0, 50))
                .total()).isEqualTo(3);
        assertThat(reports.findVisible(new ReportQuery(project, ReportStatus.DELETING, null, 0, 50)).items())
                .isEmpty();
        assertThat(reports.findById(deleted.id())).isPresent();
    }

    @Test
    void lockingNeedsATransaction() {
        assertThatThrownBy(() -> reports.lockById(UUID.randomUUID()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void aLockedReportMakesTheNextWriterWait() throws Exception {
        var report = reports.save(queued(PROJECTS.incrementAndGet(), NOW));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);

        var holder = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(status -> {
            reports.lockById(report.id()).orElseThrow();
            locked.countDown();
            await(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        var waiter = CompletableFuture.supplyAsync(() -> transactions.execute(status -> {
            var current = reports.lockById(report.id()).orElseThrow();
            current.markDeleting(NOW.plusSeconds(5));
            return reports.save(current).status();
        }));

        Thread.sleep(500);
        assertThat(waiter).isNotDone();
        release.countDown();

        assertThat(waiter.get(10, TimeUnit.SECONDS)).isEqualTo(ReportStatus.DELETING);
        holder.get(10, TimeUnit.SECONDS);
    }

    @Test
    void theSchemaRejectsAReadyRowWithoutItsFile() {
        var entity = ReportEntityMapper.toEntity(ready(PROJECTS.incrementAndGet(), NOW));
        entity.setSha256(null);

        assertThatThrownBy(() -> rows.saveAndFlush(entity)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Report queued(long project, Instant requestedAt) {
        var period = new ReportPeriod(requestedAt.minus(Duration.ofDays(1)), requestedAt);
        return Report.queueGeneration(UUID.randomUUID(), project, 7, ReportFormat.PDF,
                new GenerationParameters(period, MonitorSelection.allMonitors(), requestedAt));
    }

    private static Report ready(long project, Instant at) {
        var lease = new Lease(UUID.randomUUID(), UUID.randomUUID(), at.plusSeconds(60));
        var report = Report.startUpload(UUID.randomUUID(), project, 7, FileName.fromClient("uptime.csv"), lease, at);
        report.completeUpload(lease.token(), 10, HASH, at);
        return report;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }
}
