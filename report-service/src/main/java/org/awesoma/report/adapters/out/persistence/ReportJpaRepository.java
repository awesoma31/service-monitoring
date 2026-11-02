package org.awesoma.report.adapters.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.awesoma.report.domain.ReportSource;
import org.awesoma.report.domain.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReportJpaRepository extends JpaRepository<ReportEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReportEntity r where r.id = :id")
    Optional<ReportEntity> findForUpdate(@Param("id") UUID id);

    @Query("""
            select r from ReportEntity r
            where r.projectId = :projectId and r.status <> :hidden
              and (:status is null or r.status = :status)
              and (:source is null or r.source = :source)
            """)
    Page<ReportEntity> findVisible(@Param("projectId") long projectId, @Param("hidden") ReportStatus hidden,
            @Param("status") ReportStatus status, @Param("source") ReportSource source, Pageable page);
}
