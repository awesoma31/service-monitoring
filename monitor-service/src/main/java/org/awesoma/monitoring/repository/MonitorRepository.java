package org.awesoma.monitoring.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonitorRepository extends JpaRepository<Monitor, Long> {

    Page<Monitor> findByProjectId(Long projectId, Pageable pageable);

    /**
     * Joins rather than fetches the tags: a fetch would force Hibernate to page the result
     * in memory, loading every monitor of the project to return one page of them.
     */
    Page<Monitor> findByProjectIdAndTagsName(Long projectId, String tagName, Pageable pageable);

    boolean existsByProjectIdAndName(Long projectId, String name);

    @Query("select m.id from Monitor m where m.project.id = :projectId")
    List<Long> findIdsByProjectId(@Param("projectId") Long projectId);

    /**
     * Monitors whose interval has elapsed, oldest first. Expressed in SQL because the due
     * time depends on each monitor's own interval, which JPQL cannot add to a timestamp.
     */
    @Query(
            value = """
                    SELECT * FROM monitors
                    WHERE active
                      AND (last_checked_at IS NULL
                           OR last_checked_at + make_interval(secs => interval_sec) <= now())
                    ORDER BY last_checked_at NULLS FIRST
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<Monitor> findDue(@Param("limit") int limit);

    /**
     * Locks the monitor row for the duration of the transaction.
     *
     * <p>Two workers probing the same monitor concurrently would otherwise race over its
     * state and its open incident: both could read UP, both decide to open an incident,
     * and the second would fail on the partial unique index instead of doing nothing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Monitor> findWithLockById(Long id);
}
