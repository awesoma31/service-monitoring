package org.awesoma.monitoring.repository;

import org.awesoma.monitoring.domain.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findWithLockById(Long id);
    @Query("""
            select p from Project p where p.ownerId = :userId
            or exists (select m from ProjectMember m where m.project = p and m.id.userId = :userId)
            """)
    Page<Project> findAccessible(Long userId, Pageable pageable);

    boolean existsBySlug(String slug);
}
