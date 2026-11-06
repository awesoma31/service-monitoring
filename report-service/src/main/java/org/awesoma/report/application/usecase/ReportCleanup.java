package org.awesoma.report.application.usecase;

import java.time.Clock;
import java.time.Duration;
import org.awesoma.report.application.port.IdempotencyKeys;
import org.awesoma.report.application.port.ObjectStorage;
import org.awesoma.report.application.port.ObjectStorageUnavailableException;
import org.awesoma.report.application.port.ReportRepository;
import org.awesoma.report.application.port.TransactionRunner;
import org.awesoma.report.domain.ObjectKeys;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes what a report left in storage. Every object of a report lies under its own prefix,
 * the upload key and the key of each generation attempt alike, so one listing finds them all.
 * Deleting is idempotent; whatever fails now is retried by the background cleanup.
 */
public class ReportCleanup {
    private static final Logger log = LoggerFactory.getLogger(ReportCleanup.class);

    private final ReportRepository reports;
    private final ObjectStorage storage;
    private final IdempotencyKeys keys;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final Duration tombstoneTtl;

    public ReportCleanup(ReportRepository reports, ObjectStorage storage, IdempotencyKeys keys,
            TransactionRunner transactions, Clock clock, Duration tombstoneTtl) {
        this.reports = reports;
        this.storage = storage;
        this.keys = keys;
        this.transactions = transactions;
        this.clock = clock;
        this.tombstoneTtl = tombstoneTtl;
    }

    /**
     * Removes the objects of a DELETING report and then its row; an Idempotency-Key bound to it
     * stays taken as a tombstone for a while.
     *
     * @return whether the report is gone now
     */
    public boolean purge(Report report) {
        if (!removeObjects(report)) return false;
        boolean purged = transactions.inTransaction(() -> reports.lockById(report.id())
                .filter(current -> current.status() == ReportStatus.DELETING)
                .map(current -> {
                    reports.delete(current.id());
                    keys.tombstone(current.id(), clock.instant().plus(tombstoneTtl));
                    return true;
                })
                .orElse(false));
        if (purged) log.info("Report cleanup completed: report_id={} project_id={}", report.id(), report.projectId());
        return purged;
    }

    /** Removes one object, such as that of an attempt that will not be published. */
    public void removeObject(String key) {
        if (key == null) return;
        try {
            storage.delete(key);
        } catch (ObjectStorageUnavailableException error) {
            log.warn("Object left for reconciliation: key={}", key, error);
        }
    }

    /**
     * An uncertain commit may have published this very key. Never remove a key that can
     * still become, or already is, the current content. Attempt keys are never reused, so
     * after a durable transition away from the attempt this decision cannot be reversed.
     * If the database cannot be read, reconciliation will retry later.
     */
    public void removeUnreferenced(Report attempt) {
        try {
            var current = reports.findById(attempt.id());
            if (current.isPresent() && attempt.objectKey().equals(current.get().objectKey())
                    && (current.get().status() == ReportStatus.READY
                    || current.get().status() == ReportStatus.UPLOADING
                    || current.get().status() == ReportStatus.GENERATING)) return;
            removeObject(attempt.objectKey());
        } catch (RuntimeException error) {
            log.warn("Object cleanup deferred until reconciliation: report_id={}", attempt.id(), error);
        }
    }

    private boolean removeObjects(Report report) {
        try {
            for (var stored : storage.list(ObjectKeys.upload(report.projectId(), report.id()))) {
                storage.delete(stored.key());
            }
            return true;
        } catch (ObjectStorageUnavailableException error) {
            log.warn("Objects of a report are left for cleanup: report_id={}", report.id(), error);
            return false;
        }
    }
}
