package org.awesoma.monitoring.repository;

import java.util.Optional;
import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    @org.springframework.data.jpa.repository.Query("select i.monitor.project.id from Incident i where i.id = :id")
    java.util.Optional<Long> findProjectId(Long id);

    /** At most one row can match: the database enforces one open incident per monitor. */
    Optional<Incident> findByMonitorIdAndStatus(Long monitorId, IncidentStatus status);

    Page<Incident> findByMonitorId(Long monitorId, Pageable pageable);

    Page<Incident> findByMonitorIdAndStatus(Long monitorId, IncidentStatus status, Pageable pageable);
}
