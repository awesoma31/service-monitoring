package org.awesoma.monitoring.application.port;

import java.util.Optional;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;
public interface ProjectRepositoryPort {
    Optional<Project> findById(Long id);
    Optional<Project> findWithLockById(Long id);
    Page<Project> findAll(PageRequest page);
    Page<Project> findAccessible(Long userId, PageRequest page);
    boolean existsBySlug(String slug);
    boolean existsById(Long id);
    Project save(Project project);
    void delete(Project project);
}
