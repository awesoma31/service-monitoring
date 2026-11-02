package org.awesoma.report.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awesoma.report.application.port.EventInbox;
import org.awesoma.report.application.port.IdempotencyKeys;
import org.awesoma.report.application.port.IdempotencyKeys.IdempotencyRecord;
import org.awesoma.report.application.port.ProjectGuard;
import org.awesoma.report.application.port.ReportJobQueue;
import org.awesoma.report.application.port.ReportRepository;
import org.awesoma.report.domain.GenerationParameters;
import org.awesoma.report.domain.Lease;
import org.awesoma.report.domain.MonitorSelection;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportFormat;
import org.awesoma.report.domain.ReportPeriod;
import org.awesoma.report.domain.ReportStatus;
import org.awesoma.report.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class JobQueueIntegrationTest extends AbstractIntegrationTest {
    /** Later than any job other tests leave, so these jobs are claimed only when asked for. */
    private static final Instant FAR = Instant.parse("2099-01-01T00:00:00Z");

    @Autowired private ReportJobQueue queue;
    @Autowired private ReportRepository reports;
    @Autowired private IdempotencyKeys keys;
    @Autowired private ProjectGuard projects;
    @Autowired private EventInbox inbox;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate db;

    @Test
    void concurrentWorkersSkipAJobAnotherOneHolds() throws Exception {
        db.update("UPDATE reports SET status = 'FAILED', failure_code = 'GENERATION_ERROR' WHERE status = 'QUEUED'");
        var first = reports.save(job());
        var second = reports.save(job());
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);

        var holder = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(status -> {
            reports.lockById(first.id()).orElseThrow();
            held.countDown();
            await(release);
        }));
        assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();

        var claimed = queue.claimNext(lease(), FAR).orElseThrow();
        release.countDown();
        holder.get(10, TimeUnit.SECONDS);

        assertThat(claimed.id()).isEqualTo(second.id());
        assertThat(claimed.status()).isEqualTo(ReportStatus.GENERATING);
        assertThat(reports.findById(second.id()).orElseThrow().attempts()).isEqualTo(1);
        assertThat(queue.claimNext(lease(), FAR).orElseThrow().id()).isEqualTo(first.id());
        assertThat(queue.claimNext(lease(), FAR)).isEmpty();
        assertThat(queue.expiredLeases(FAR.plusSeconds(3600), 10)).contains(first.id(), second.id());
    }

    @Test
    void anIdempotencyKeyIsBoundOnceAndThenTurnsIntoATombstone() {
        var report = UUID.randomUUID();
        var record = new IdempotencyRecord(7, 42, "key-" + report, "f", report, null);

        assertThat(keys.bind(record)).isTrue();
        assertThat(keys.bind(record)).isFalse();
        keys.tombstone(report, FAR);
        assertThat(keys.find(7, 42, record.key()).orElseThrow().expiresAt()).isEqualTo(FAR);
        assertThat(keys.removeExpired(FAR)).isGreaterThanOrEqualTo(1);
        assertThat(keys.find(7, 42, record.key())).isEmpty();
    }

    @Test
    void theProjectMarkerAndInboxLiveInTheTransaction() {
        long project = 991_001;
        UUID event = UUID.randomUUID();

        Boolean first = transactions.execute(status -> {
            projects.lock(project);
            projects.markDeleted(project);
            return inbox.record(event);
        });
        Boolean again = transactions.execute(status -> inbox.record(event));

        assertThat(first).isTrue();
        assertThat(again).isFalse();
        Boolean marked = transactions.execute(status -> projects.deleted(project));
        Boolean other = transactions.execute(status -> projects.deleted(project + 1));
        assertThat(marked).isTrue();
        assertThat(other).isFalse();
    }

    private static Report job() {
        return Report.queueGeneration(UUID.randomUUID(), 42, 7, ReportFormat.CSV, new GenerationParameters(
                new ReportPeriod(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-02T00:00:00Z")),
                MonitorSelection.allMonitors(), Instant.parse("2026-11-01T00:00:00Z")));
    }

    private static Lease lease() {
        return new Lease(UUID.randomUUID(), UUID.randomUUID(), FAR.plusSeconds(60));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }
}
