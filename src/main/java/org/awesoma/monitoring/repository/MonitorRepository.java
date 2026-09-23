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

    Page<Monitor> findByProjectIdAndTagsName(Long projectId, String tagName, Pageable pageable);

    boolean existsByProjectIdAndName(Long projectId, String name);

    @Query(
            value = """
                    SELECT id FROM monitors
                    WHERE active
                      AND (last_checked_at IS NULL
                           OR last_checked_at + make_interval(secs => interval_sec) <= now())
                    ORDER BY last_checked_at NULLS FIRST
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<Long> findDueMonitorIds(@Param("limit") int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Monitor m where m.id = :id")
    Optional<Monitor> findByIdForUpdate(@Param("id") Long id);
}
