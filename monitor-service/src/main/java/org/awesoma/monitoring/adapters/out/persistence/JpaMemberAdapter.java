package org.awesoma.monitoring.adapters.out.persistence;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.adapters.out.persistence.entity.Project;
import org.awesoma.monitoring.adapters.out.persistence.repository.ProjectMemberRepository;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;
import org.awesoma.monitoring.application.port.MemberRepositoryPort;
import org.awesoma.monitoring.domain.entity.ProjectMember;
import org.awesoma.monitoring.domain.entity.ProjectMemberId;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaMemberAdapter implements MemberRepositoryPort {
    private final ProjectMemberRepository rows;
    private final EntityManager em;
    private org.awesoma.monitoring.adapters.out.persistence.entity.ProjectMemberId key(ProjectMemberId id) {
        return new org.awesoma.monitoring.adapters.out.persistence.entity.ProjectMemberId(id.getProjectId(),
                id.getUserId());
    }

    @Override
    public Page<ProjectMember> findByProjectId(Long id, PageRequest page) {
        return PersistenceMapping.page(rows.findByProjectId(id, PersistenceMapping.pageable(page)),
                PersistenceMapping::member);
    }

    @Override
    public boolean existsById(ProjectMemberId id) {
        return rows.existsById(key(id));
    }

    @Override
    public Optional<ProjectMember> findById(ProjectMemberId id) {
        return rows.findById(key(id)).map(PersistenceMapping::member);
    }

    @Override
    public ProjectMember saveAndFlush(ProjectMember source) {
        var project = em.getReference(Project.class, source.getProject().getId());
        var target = new org.awesoma.monitoring.adapters.out.persistence.entity.ProjectMember(project,
                source.getUserId());
        return PersistenceMapping.member(rows.saveAndFlush(target));
    }

    @Override
    public void delete(ProjectMember source) {
        rows.deleteById(key(source.getId()));
    }
}
