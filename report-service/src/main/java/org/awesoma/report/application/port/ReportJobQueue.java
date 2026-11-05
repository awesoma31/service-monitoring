package org.awesoma.report.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.awesoma.report.domain.Lease;
import org.awesoma.report.domain.Report;

/** Durable generation jobs shared by every instance of the service. */
public interface ReportJobQueue {

    /**
     * Atomically takes one due job that no other instance is taking, starts an attempt with
     * the given lease and commits, so the work itself runs outside any transaction.
     */
    Optional<Report> claimNext(Lease lease, Instant now);

    /** Jobs whose worker stopped renewing its lease, for recovery after a crash. */
    List<UUID> expiredLeases(Instant now, int limit);

    /** Uploads whose writer has not completed before its lease expired. */
    List<UUID> expiredUploads(Instant now, int limit);
}
