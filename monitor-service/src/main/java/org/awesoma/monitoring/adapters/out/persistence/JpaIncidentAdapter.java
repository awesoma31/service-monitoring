package org.awesoma.monitoring.adapters.out.persistence;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;
import org.awesoma.monitoring.application.port.IncidentRepositoryPort;
import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.awesoma.monitoring.repository.IncidentRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaIncidentAdapter implements IncidentRepositoryPort {
    private final IncidentRepository rows;
    private final EntityManager em;
    public Optional<Incident> findById(Long id) {
        return rows.findById(id).map(PersistenceMapping::incident);
    }
    public Optional<Long> findProjectId(Long id) {
        return rows.findProjectId(id);
    }
    public Optional<Incident> findByMonitorIdAndStatus(Long id,IncidentStatus status) {
        return rows.findByMonitorIdAndStatus(id,status).map(PersistenceMapping::incident);
    }
    public Page<Incident> findByMonitorId(Long id,PageRequest page) {
        return PersistenceMapping.page(rows.findByMonitorId(id,PersistenceMapping.pageable(page)),PersistenceMapping::incident);
    }
    public Page<Incident> findByMonitorIdAndStatus(Long id,IncidentStatus status,PageRequest page) {
        return PersistenceMapping.page(rows.findByMonitorIdAndStatus(id,status,PersistenceMapping.pageable(page)),PersistenceMapping::incident);
    }
    public boolean existsById(Long id) {
        return rows.existsById(id);
    }
    public long maxId() {
        return rows.maxId();
    }
    public List<Incident> findReportPage(Collection<Long> ids,OffsetDateTime from,OffsetDateTime to,long after,long upper,int limit) {
        return rows.findReportPage(ids,from,to,after,upper,Limit.of(limit)).stream().map(PersistenceMapping::incident).toList();
    }
    public Incident save(Incident source) {
    var target=source.getId()==null ? new org.awesoma.monitoring.adapters.out.persistence.entity.Incident() : rows.findById(source.getId()).orElseThrow();
    target.setMonitor(em.getReference(org.awesoma.monitoring.adapters.out.persistence.entity.Monitor.class,source.getMonitor().getId()));
    target.setStartedAt(source.getStartedAt());
    target.setResolvedAt(source.getResolvedAt());
    target.setStatus(source.getStatus());
    target.setSeverity(source.getSeverity());
    target.setCause(source.getCause());
    return PersistenceMapping.incident(rows.save(target));
    }
}
