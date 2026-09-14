package org.awesoma.monitoring.repository;

import org.awesoma.monitoring.domain.entity.Monitor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitorRepository extends JpaRepository<Monitor, Long> {

    Page<Monitor> findByProjectId(Long projectId, Pageable pageable);

    /**
     * Joins rather than fetches the tags: a fetch would force Hibernate to page the result
     * in memory, loading every monitor of the project to return one page of them.
     */
    Page<Monitor> findByProjectIdAndTagsName(Long projectId, String tagName, Pageable pageable);

    boolean existsByProjectIdAndName(Long projectId, String name);
}
