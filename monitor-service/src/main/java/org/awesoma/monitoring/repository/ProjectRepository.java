package org.awesoma.monitoring.repository;

import org.awesoma.monitoring.domain.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select p from Project p where p.ownerId = :userId
            or exists (select m from ProjectMember m where m.project = p and m.id.userId = :userId)
            """)
    org.springframework.data.domain.Page<Project> findAccessible(Long userId, org.springframework.data.domain.Pageable pageable);

    boolean existsBySlug(String slug);
}
