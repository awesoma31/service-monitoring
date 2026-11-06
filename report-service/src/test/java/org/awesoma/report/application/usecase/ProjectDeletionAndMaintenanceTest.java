package org.awesoma.report.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.awesoma.report.application.port.IdempotencyKeys.IdempotencyRecord;
import org.awesoma.report.domain.FileName;
import org.awesoma.report.domain.GenerationParameters;
import org.awesoma.report.domain.Lease;
import org.awesoma.report.domain.MonitorSelection;
import org.awesoma.report.domain.ObjectKeys;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportFormat;
import org.awesoma.report.domain.ReportPeriod;
import org.awesoma.report.domain.ReportStatus;
import org.junit.jupiter.api.Test;

class ProjectDeletionAndMaintenanceTest {
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final String HASH = "c".repeat(64);

    private final Fakes.Reports reports = new Fakes.Reports();
    private final Fakes.Storage storage = new Fakes.Storage();
    private final Fakes.Keys keys = new Fakes.Keys();
    private final Fakes.Projects projects = new Fakes.Projects();
    private final MutableClock clock = new MutableClock(NOW);
    private final ReportCleanup cleanup = new ReportCleanup(reports, storage, keys, Fakes.TRANSACTIONS, clock,
            Duration.ofHours(24));
    private final ProjectDeletionService deletions = new ProjectDeletionService(reports, projects, projects,
            Fakes.TRANSACTIONS, clock);
    private final ReportMaintenance maintenance = new ReportMaintenance(reports, storage, keys, cleanup, clock,
            Duration.ofMinutes(15));

    @Test
    void aDeletedProjectHidesEveryReportAndJobOnce() {
        var ready = ready(42);
        var generating = queued(42);
        generating.startAttempt(new Lease(UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(60)), NOW);
        reports.save(generating);
        var other = ready(43);
        UUID event = UUID.randomUUID();

        assertThat(deletions.projectDeleted(event, 42)).isEqualTo(2);
        assertThat(deletions.projectDeleted(event, 42)).as("repeated event").isZero();

        assertThat(reports.row(ready.id()).status()).isEqualTo(ReportStatus.DELETING);
        assertThat(reports.row(generating.id()).status()).isEqualTo(ReportStatus.DELETING);
        assertThat(reports.row(generating.id()).lease()).isNull();
        assertThat(reports.row(other.id()).status()).isEqualTo(ReportStatus.READY);
        assertThat(projects.deleted).containsExactly(42L);
        assertThat(projects.locked).containsExactly(42L);
    }

    @Test
    void aDeletedMonitorChangesNoReport() {
        var ready = ready(42);
        UUID event = UUID.randomUUID();

        deletions.monitorDeleted(event);

        assertThat(projects.events).containsExactly(event);
        assertThat(reports.row(ready.id()).status()).isEqualTo(ReportStatus.READY);
    }

    @Test
    void cleanupRemovesTheObjectsThenTheRowAndKeepsTheKeyTakenForAWhile() {
        var ready = ready(42);
        storage.objects.put(ObjectKeys.attempt(42, ready.id(), UUID.randomUUID()), new byte[] {2});
        keys.bind(new IdempotencyRecord(7, 42, "key", "f", ready.id(), null));
        deletions.projectDeleted(UUID.randomUUID(), 42);

        storage.deleteFails = true;
        assertThat(maintenance.purgeDeleted(10)).isZero();
        storage.deleteFails = false;
        assertThat(maintenance.purgeDeleted(10)).isEqualTo(1);

        assertThat(reports.rows).isEmpty();
        assertThat(storage.objects).isEmpty();
        assertThat(keys.find(7, 42, "key").orElseThrow().tombstoneAt(NOW)).isTrue();
        clock.advance(Duration.ofHours(24));
        assertThat(maintenance.removeExpiredKeys()).isEqualTo(1);
    }

    @Test
    void reconciliationRemovesOnlyOldUnreferencedObjects() {
        var ready = ready(42);
        var failedAttempt = ObjectKeys.attempt(42, ready.id(), UUID.randomUUID());
        var orphan = ObjectKeys.upload(42, UUID.randomUUID());
        var stranger = "projects/42/reports/not-a-uuid";
        for (String key : new String[] {failedAttempt, orphan, stranger, ready.objectKey()}) {
            storage.objects.put(key, new byte[] {1});
            storage.written.put(key, NOW.minus(Duration.ofHours(1)));
        }
        var fresh = ObjectKeys.upload(42, UUID.randomUUID());
        storage.objects.put(fresh, new byte[] {1});
        storage.written.put(fresh, NOW.minusSeconds(60));

        assertThat(maintenance.reconcile()).isEqualTo(3);

        assertThat(storage.objects.keySet()).containsExactlyInAnyOrder(ready.objectKey(), fresh);
    }

    private Report ready(long projectId) {
        var lease = new Lease(UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(60));
        var report = Report.startUpload(UUID.randomUUID(), projectId, 7, FileName.fromClient("r.csv"), lease, NOW);
        report.completeUpload(lease.token(), 1, HASH, NOW);
        storage.objects.put(report.objectKey(), new byte[] {1});
        storage.written.put(report.objectKey(), NOW);
        return reports.save(report);
    }

    private Report queued(long projectId) {
        return reports.save(Report.queueGeneration(UUID.randomUUID(), projectId, 7, ReportFormat.PDF,
                new GenerationParameters(new ReportPeriod(NOW.minusSeconds(3600), NOW), MonitorSelection.allMonitors(),
                        NOW)));
    }
}
