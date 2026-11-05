package org.awesoma.monitoring.application.port;

import java.util.Optional;
import org.awesoma.monitoring.domain.entity.ProjectMember;
import org.awesoma.monitoring.domain.entity.ProjectMemberId;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;
public interface MemberRepositoryPort {
    Page<ProjectMember> findByProjectId(Long projectId, PageRequest page);
    boolean existsById(ProjectMemberId id);
    Optional<ProjectMember> findById(ProjectMemberId id);
    ProjectMember saveAndFlush(ProjectMember member);
    void delete(ProjectMember member);
}
