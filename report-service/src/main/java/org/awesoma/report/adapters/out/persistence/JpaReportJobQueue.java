package org.awesoma.report.adapters.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.awesoma.report.application.port.ReportJobQueue;
import org.awesoma.report.domain.Lease;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportStatus;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The queue is the reports table itself: a due QUEUED row is a job. */
@Component
@RequiredArgsConstructor
class JpaReportJobQueue implements ReportJobQueue {
    private final ReportJpaRepository rows;

    /** Its own transaction: the claim is committed before the work starts. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Report> claimNext(Lease lease, Instant now) {
        return rows.lockNextDue(now)
                .flatMap(rows::findById)
                .map(ReportEntityMapper::toDomain)
                .map(report -> {
                    report.startAttempt(lease, now);
                    return ReportEntityMapper.toDomain(rows.save(ReportEntityMapper.toEntity(report)));
                });
    }

    @Override
    public List<UUID> expiredLeases(Instant now, int limit) {
        return rows.findExpiredLeases(ReportStatus.GENERATING, now, Limit.of(limit));
    }

    @Override
    public List<UUID> expiredUploads(Instant now, int limit) {
        return rows.findExpiredLeases(ReportStatus.UPLOADING, now, Limit.of(limit));
    }
}
