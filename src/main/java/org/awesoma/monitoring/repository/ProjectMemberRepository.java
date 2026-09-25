package org.awesoma.monitoring.repository;

import java.util.Optional;
import org.awesoma.monitoring.domain.entity.ProjectMember;
import org.awesoma.monitoring.domain.entity.ProjectMemberId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {

    Page<ProjectMember> findByProjectId(Long projectId, Pageable pageable);

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

}
