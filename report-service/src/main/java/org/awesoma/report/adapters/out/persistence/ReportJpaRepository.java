package org.awesoma.report.adapters.out.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.awesoma.report.domain.ReportSource;
import org.awesoma.report.domain.ReportStatus;
import org.springframework.data.domain.Limit;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReportEntity r where r.projectId = :projectId and r.status <> :hidden order by r.id")
    List<ReportEntity> lockOfProject(@Param("projectId") long projectId, @Param("hidden") ReportStatus hidden);

    @Query("select r.id from ReportEntity r where r.status = :status order by r.updatedAt")
    List<UUID> findIdsByStatus(@Param("status") ReportStatus status, Limit limit);

    /**
     * The next due job that no other instance holds. SKIP LOCKED makes concurrent workers take
     * different rows instead of waiting for one another and then taking the same job.
     */
    @Query(value = """
            SELECT id FROM reports
            WHERE status = 'QUEUED' AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            ORDER BY next_attempt_at NULLS FIRST, created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<UUID> lockNextDue(@Param("now") Instant now);

    @Query("select r.id from ReportEntity r where r.status = :status and r.leaseUntil <= :now order by r.leaseUntil")
    List<UUID> findExpiredLeases(@Param("status") ReportStatus status, @Param("now") Instant now, Limit limit);
}
