package org.awesoma.monitoring.repository;

import org.awesoma.monitoring.domain.entity.Monitor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitorRepository extends JpaRepository<Monitor, Long> {

    /**
     * Tags are fetched alongside the page: without the graph, rendering a page of monitors
     * issues one extra query per row.
     */
    @EntityGraph(attributePaths = "tags")
    Page<Monitor> findByProjectId(Long projectId, Pageable pageable);

    boolean existsByProjectIdAndName(Long projectId, String name);
}
