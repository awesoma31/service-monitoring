package org.awesoma.monitoring.adapters.out.persistence.repository;

import org.awesoma.monitoring.adapters.out.persistence.entity.ProjectMember;
import org.awesoma.monitoring.adapters.out.persistence.entity.ProjectMemberId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {

    Page<ProjectMember> findByProjectId(Long projectId, Pageable pageable);


}
