package org.awesoma.monitoring.adapters.out.persistence;

import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;
import org.awesoma.monitoring.application.port.ProjectRepositoryPort;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.repository.ProjectRepository;
import org.springframework.stereotype.Component;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JpaProjectAdapter implements ProjectRepositoryPort {
    private final ProjectRepository rows;
    public Optional<Project> findById(Long id) {
        return rows.findById(id).map(PersistenceMapping::project);
    }
    public Optional<Project> findWithLockById(Long id) {
        return rows.findWithLockById(id).map(PersistenceMapping::project);
    }
    public Page<Project> findAll(PageRequest page) {
        return PersistenceMapping.page(rows.findAll(PersistenceMapping.pageable(page)),PersistenceMapping::project);
    }
    public Page<Project> findAccessible(Long id,PageRequest page) {
        return PersistenceMapping.page(rows.findAccessible(id,PersistenceMapping.pageable(page)),PersistenceMapping::project);
    }
    public boolean existsBySlug(String slug) {
        return rows.existsBySlug(slug);
    }
    public boolean existsById(Long id) {
        return rows.existsById(id);
    }
    public Project save(Project source) {
    var target=source.getId()==null ? new org.awesoma.monitoring.adapters.out.persistence.entity.Project() : rows.findById(source.getId()).orElseThrow();
    boolean fresh=source.getId()==null;
    target.setOwnerId(source.getOwnerId());
    target.setName(source.getName());
    target.setSlug(source.getSlug());
    target.setOwnerNotificationsEnabled(source.isOwnerNotificationsEnabled());
    if (fresh) source.getMembers().forEach(member -> target.addMember(member.getUserId()));
    return PersistenceMapping.project(rows.save(target));
    }
    public void delete(Project source) {
        rows.deleteById(source.getId());
    }
}
