package org.awesoma.monitoring.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;
public interface MonitorRepositoryPort {
    Optional<Monitor> findById(Long id);
    Optional<Monitor> findWithLockById(Long id);
    Optional<Long> findProjectId(Long id);
    Page<Monitor> findByProjectId(Long id, PageRequest page);
    Page<Monitor> findByProjectIdAndTagsName(Long id, String tag, PageRequest page);
    boolean existsByProjectIdAndName(Long projectId, String name);
    boolean existsByProjectIdAndNameAndIdNot(Long projectId, String name, Long id);
    boolean existsById(Long id);
    List<Long> findIdsByProjectId(Long id);
    long countByProjectIdAndIdIn(Long id, Collection<Long> ids);
    long maxId();
    /** Monitors are returned without project and tags, and are only good for reading. */
    List<Monitor> findReportPage(Long projectId, boolean all, Collection<Long> ids, long after, long upper, int limit);
    /** Monitors are returned without project and tags: a claim only needs {@link #saveClaim}. */
    List<Monitor> findDueForClaim(int limit);
    void saveClaim(Monitor monitor);
    Monitor save(Monitor monitor);
    void delete(Monitor monitor);
}
