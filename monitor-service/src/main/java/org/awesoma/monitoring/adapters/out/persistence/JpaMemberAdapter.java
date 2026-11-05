package org.awesoma.monitoring.adapters.out.persistence;

import jakarta.persistence.EntityManager;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;
import org.awesoma.monitoring.application.port.MemberRepositoryPort;
import org.awesoma.monitoring.domain.entity.ProjectMember;
import org.awesoma.monitoring.domain.entity.ProjectMemberId;
import org.awesoma.monitoring.repository.ProjectMemberRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaMemberAdapter implements MemberRepositoryPort {
    private final ProjectMemberRepository rows;
    private final EntityManager em;

    private org.awesoma.monitoring.adapters.out.persistence.entity.ProjectMemberId key(ProjectMemberId id) {
        return new org.awesoma.monitoring.adapters.out.persistence.entity.ProjectMemberId(id.getProjectId(), id.getUserId());
    }

    public Page<ProjectMember> findByProjectId(Long id, PageRequest page) {
        return PersistenceMapping.page(rows.findByProjectId(id, PersistenceMapping.pageable(page)), PersistenceMapping::member);
    }

    public boolean existsById(ProjectMemberId id) {
        return rows.existsById(key(id));
    }

    public Optional<ProjectMember> findById(ProjectMemberId id) {
        return rows.findById(key(id)).map(PersistenceMapping::member);
    }

    public ProjectMember saveAndFlush(ProjectMember source) {
        var project = em.getReference(org.awesoma.monitoring.adapters.out.persistence.entity.Project.class, source.getProject().getId());
        var target = new org.awesoma.monitoring.adapters.out.persistence.entity.ProjectMember(project, source.getUserId());
        return PersistenceMapping.member(rows.saveAndFlush(target));
    }

    public void delete(ProjectMember source) {
        rows.deleteById(key(source.getId()));
    }
}
