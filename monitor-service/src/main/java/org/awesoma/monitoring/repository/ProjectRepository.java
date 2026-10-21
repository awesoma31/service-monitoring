package org.awesoma.monitoring.repository;

import org.awesoma.monitoring.domain.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @Query("""
            select p from Project p where p.ownerId = :userId
            or exists (select m from ProjectMember m where m.project = p and m.id.userId = :userId)
            """)
    Page<Project> findAccessible(Long userId, Pageable pageable);

    boolean existsBySlug(String slug);
}
