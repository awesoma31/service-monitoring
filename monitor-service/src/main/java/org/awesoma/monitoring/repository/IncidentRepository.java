package org.awesoma.monitoring.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.awesoma.monitoring.adapters.out.persistence.entity.Incident;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    @Query("select i.monitor.project.id from Incident i where i.id = :id")
    Optional<Long> findProjectId(Long id);

    /** At most one row can match: the database enforces one open incident per monitor. */
    Optional<Incident> findByMonitorIdAndStatus(Long monitorId, IncidentStatus status);

    Page<Incident> findByMonitorId(Long monitorId, Pageable pageable);

    Page<Incident> findByMonitorIdAndStatus(Long monitorId, IncidentStatus status, Pageable pageable);

    @Query("select coalesce(max(i.id), 0) from Incident i")
    long maxId();

    /**
     * One keyset page of the incidents whose interval overlaps {@code [from, to)}: started
     * before the end and not resolved by the start. One resolved exactly at {@code from}, or
     * started exactly at {@code to}, is outside.
     */
    @Query("""
            select i from Incident i
            where i.monitor.id in :monitorIds
              and i.startedAt < :to and (i.resolvedAt is null or i.resolvedAt > :from)
              and i.id > :after and i.id <= :upper
            order by i.id
            """)
    List<Incident> findReportPage(@Param("monitorIds") Collection<Long> monitorIds,
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
            @Param("after") long after, @Param("upper") long upper, Limit limit);
}
