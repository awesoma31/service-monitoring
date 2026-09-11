package org.awesoma.monitoring.repository;

import org.awesoma.monitoring.domain.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsBySlug(String slug);
}
