package org.awesoma.report.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.awesoma.report.application.port.IdempotencyKeys;
import org.awesoma.report.application.port.ObjectStorage;
import org.awesoma.report.application.port.ObjectStorageUnavailableException;
import org.awesoma.report.application.port.ReportRepository;
import org.awesoma.report.application.port.StoredKey;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background housekeeping: removes deleted reports, expired Idempotency-Key tombstones and
 * objects nothing refers to — of failed or superseded attempts, of removed reports, or put
 * there by a late writer after its lease ended.
 */
public class ReportMaintenance {
    private static final Logger log = LoggerFactory.getLogger(ReportMaintenance.class);
    private static final String ROOT = "projects/";

    private final ReportRepository reports;
    private final ObjectStorage storage;
    private final IdempotencyKeys keys;
    private final ReportCleanup cleanup;
    private final Clock clock;
    private final Duration reconciliationGrace;

    public ReportMaintenance(ReportRepository reports, ObjectStorage storage, IdempotencyKeys keys,
            ReportCleanup cleanup, Clock clock, Duration reconciliationGrace) {
        this.reports = reports;
        this.storage = storage;
        this.keys = keys;
        this.cleanup = cleanup;
        this.clock = clock;
        this.reconciliationGrace = reconciliationGrace;
    }

    /** @return how many deleted reports are gone now */
    public int purgeDeleted(int limit) {
        int purged = 0;
        for (UUID id : reports.findDeleting(limit)) {
            if (reports.findById(id).map(cleanup::purge).orElse(false)) purged++;
        }
        return purged;
    }

    public int removeExpiredKeys() {
        return keys.removeExpired(clock.instant());
    }

    /**
     * Objects younger than the grace period are left alone: a writer may still be at work. A late
     * object is caught by a later run.
     *
     * @return how many objects were removed
     */
    public int reconcile() {
        var cutoff = clock.instant().minus(reconciliationGrace);
        int removed = 0;
        try {
            for (StoredKey stored : storage.list(ROOT)) {
                if (!stored.lastModified().isBefore(cutoff) || referenced(stored.key())) continue;
                storage.delete(stored.key());
                log.info("Unreferenced report object removed: key={}", stored.key());
                removed++;
            }
        } catch (ObjectStorageUnavailableException error) {
            log.warn("Reconciliation stopped, storage unavailable", error);
        }
        return removed;
    }

    /**
     * Only the current object of a ready report, or of an upload or attempt in progress, is
     * kept. A deleting report's objects are left to {@link #purgeDeleted}, which also removes
     * its row; everything else is unreferenced.
     */
    private boolean referenced(String key) {
        Optional<Report> report = reportOf(key).flatMap(reports::findById);
        if (report.isEmpty()) return false;
        if (report.get().status() == ReportStatus.DELETING) return true;
        return key.equals(report.get().objectKey()) && switch (report.get().status()) {
            case READY, UPLOADING, GENERATING -> true;
            default -> false;
        };
    }

    /** {@code projects/{projectId}/reports/{reportId}[/attempts/{attemptId}]} */
    private static Optional<UUID> reportOf(String key) {
        String[] parts = key.split("/");
        if (parts.length < 4) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(parts[3]));
        } catch (IllegalArgumentException notAReportKey) {
            return Optional.empty();
        }
    }
}
