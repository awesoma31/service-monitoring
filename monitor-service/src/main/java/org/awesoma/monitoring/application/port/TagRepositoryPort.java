package org.awesoma.monitoring.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.awesoma.monitoring.domain.entity.Tag;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;
public interface TagRepositoryPort {
    Page<Tag> findAll(PageRequest page);
    List<Tag> findByNameIn(Collection<String> names);
    boolean existsByName(String name);
    Optional<Tag> findById(Long id);
    Tag save(Tag tag);
    void delete(Tag tag);
}
