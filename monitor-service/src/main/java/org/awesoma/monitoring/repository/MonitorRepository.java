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

    boolean existsByProjectIdAndNameAndIdNot(Long projectId, String name, Long id);

    @Query("select m.id from Monitor m where m.project.id = :projectId")
    List<Long> findIdsByProjectId(@Param("projectId") Long projectId);

    /**
     * Locks a disjoint batch of due monitors. SKIP LOCKED lets concurrent check-service
     * instances claim different rows rather than wait for and then duplicate the same batch.
     */
    @Query(
            value = """
                    SELECT * FROM monitors
                    WHERE active
                      AND (last_checked_at IS NULL
                           OR last_checked_at + make_interval(secs => interval_sec) <= now())
                      AND (check_claimed_until IS NULL OR check_claimed_until <= now())
                    ORDER BY last_checked_at NULLS FIRST
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<Monitor> findDueForClaim(@Param("limit") int limit);

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
