package org.awesoma.report.adapters.out.persistence;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.awesoma.report.application.port.ReportPage;
import org.awesoma.report.application.port.ReportQuery;
import org.awesoma.report.application.port.ReportRepository;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class JpaReportRepository implements ReportRepository {
    /** Newest first; the ID breaks ties so that pages do not overlap or skip rows. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ReportJpaRepository rows;

    @Override
    public Optional<Report> findById(UUID id) {
        return rows.findById(id).map(ReportEntityMapper::toDomain);
    }

    /** A lock outside a transaction would be released at once, so one must already be open. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Report> lockById(UUID id) {
        return rows.findForUpdate(id).map(ReportEntityMapper::toDomain);
    }

    @Override
    @Transactional
    public Report save(Report report) {
        return ReportEntityMapper.toDomain(rows.save(ReportEntityMapper.toEntity(report)));
    }

    @Override
    public ReportPage findVisible(ReportQuery query) {
        var page = rows.findVisible(query.projectId(), ReportStatus.DELETING, query.status(), query.source(),
                PageRequest.of(query.page(), query.size(), NEWEST_FIRST));
        return new ReportPage(page.map(ReportEntityMapper::toDomain).getContent(), page.getTotalElements(),
                page.hasNext());
    }
}
