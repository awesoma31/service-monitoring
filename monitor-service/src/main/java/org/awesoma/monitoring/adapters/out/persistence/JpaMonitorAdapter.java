package org.awesoma.monitoring.adapters.out.persistence;

import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.adapters.out.persistence.entity.Tag;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;
import org.awesoma.monitoring.application.port.MonitorRepositoryPort;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.repository.MonitorRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaMonitorAdapter implements MonitorRepositoryPort {
    private final MonitorRepository rows;
    private final EntityManager em;
    public Optional<Monitor> findById(Long id) {
        return rows.findById(id).map(PersistenceMapping::monitor);
    }
    public Optional<Monitor> findWithLockById(Long id) {
        return rows.findWithLockById(id).map(PersistenceMapping::monitor);
    }
    public Optional<Long> findProjectId(Long id) {
        return rows.findProjectId(id);
    }
    public Page<Monitor> findByProjectId(Long id,PageRequest page) {
        return PersistenceMapping.page(rows.findByProjectId(id,PersistenceMapping.pageable(page)),PersistenceMapping::monitor);
    }
    public Page<Monitor> findByProjectIdAndTagsName(Long id,String tag,PageRequest page) {
        return PersistenceMapping.page(rows.findByProjectIdAndTagsName(id,tag,PersistenceMapping.pageable(page)),PersistenceMapping::monitor);
    }
    public boolean existsByProjectIdAndName(Long projectId,String name) {
        return rows.existsByProjectIdAndName(projectId,name);
    }
    public boolean existsByProjectIdAndNameAndIdNot(Long projectId,String name,Long id) {
        return rows.existsByProjectIdAndNameAndIdNot(projectId,name,id);
    }
    public boolean existsById(Long id) {
        return rows.existsById(id);
    }
    public List<Long> findIdsByProjectId(Long id) {
        return rows.findIdsByProjectId(id);
    }
    public long countByProjectIdAndIdIn(Long id,Collection<Long> ids) {
        return rows.countByProjectIdAndIdIn(id,ids);
    }
    public long maxId() {
        return rows.maxId();
    }
    public List<Monitor> findReportPage(Long id,boolean all,Collection<Long> ids,long after,long upper,int limit) {
        return rows.findReportPage(id,all,ids,after,upper,Limit.of(limit)).stream().map(PersistenceMapping::monitor).toList();
    }
    public List<Monitor> findDueForClaim(int limit) {
        return rows.findDueForClaim(limit).stream().map(PersistenceMapping::monitor).toList();
    }
    public Monitor save(Monitor source) {
    var target=source.getId()==null ? new org.awesoma.monitoring.adapters.out.persistence.entity.Monitor() : rows.findById(source.getId()).orElseThrow();
    target.setProject(em.getReference(org.awesoma.monitoring.adapters.out.persistence.entity.Project.class,source.getProject().getId()));
    target.setName(source.getName());
    target.setUrl(source.getUrl());
    target.setHttpMethod(source.getHttpMethod());
    target.setIntervalSec(source.getIntervalSec());
    target.setTimeoutMs(source.getTimeoutMs());
    target.setExpectedStatus(source.getExpectedStatus());
    target.setActive(source.isActive());
    target.setCurrentState(source.getCurrentState());
    target.setLastCheckedAt(source.getLastCheckedAt());
    target.setCheckClaimToken(source.getCheckClaimToken());
    target.setCheckClaimedUntil(source.getCheckClaimedUntil());
    syncTags(target, source);
    return PersistenceMapping.monitor(rows.save(target));
    }

    /**
     * Changes the loaded collection in place: Hibernate then writes only the rows that differ,
     * while a new collection would make it delete and re-insert all of them on every save.
     */
    private void syncTags(org.awesoma.monitoring.adapters.out.persistence.entity.Monitor target, Monitor source) {
        Set<Tag> wanted = source.getTags().stream()
                .map(tag -> em.getReference(Tag.class, tag.getId()))
                .collect(Collectors.toSet());
        target.getTags().retainAll(wanted);
        target.getTags().addAll(wanted);
    }

    public void delete(Monitor source) {
        rows.deleteById(source.getId());
    }
}
