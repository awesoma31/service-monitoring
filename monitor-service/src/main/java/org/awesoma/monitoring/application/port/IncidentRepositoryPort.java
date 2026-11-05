package org.awesoma.monitoring.application.port;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;
public interface IncidentRepositoryPort {
    Optional<Incident> findById(Long id);
    Optional<Long> findProjectId(Long id);
    Optional<Incident> findByMonitorIdAndStatus(Long monitorId, IncidentStatus status);
    Page<Incident> findByMonitorId(Long monitorId, PageRequest page);
    Page<Incident> findByMonitorIdAndStatus(Long monitorId, IncidentStatus status, PageRequest page);
    boolean existsById(Long id);
    long maxId();
    List<Incident> findReportPage(Collection<Long> ids, OffsetDateTime from, OffsetDateTime to, long after, long upper, int limit);
    Incident save(Incident incident);
}
