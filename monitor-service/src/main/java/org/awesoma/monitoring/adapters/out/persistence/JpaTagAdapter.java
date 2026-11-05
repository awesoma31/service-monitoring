package org.awesoma.monitoring.adapters.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;
import org.awesoma.monitoring.application.port.TagRepositoryPort;
import org.awesoma.monitoring.domain.entity.Tag;
import org.awesoma.monitoring.repository.TagRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaTagAdapter implements TagRepositoryPort {
    private final TagRepository rows;

    public Page<Tag> findAll(PageRequest page) {
        return PersistenceMapping.page(rows.findAll(PersistenceMapping.pageable(page)), PersistenceMapping::tag);
    }

    public List<Tag> findByNameIn(Collection<String> names) {
        return rows.findByNameIn(names).stream().map(PersistenceMapping::tag).toList();
    }

    public boolean existsByName(String name) {
        return rows.existsByName(name);
    }

    public Optional<Tag> findById(Long id) {
        return rows.findById(id).map(PersistenceMapping::tag);
    }

    public Tag save(Tag source) {
        var target = source.getId() == null ? new org.awesoma.monitoring.adapters.out.persistence.entity.Tag() : rows.findById(source.getId()).orElseThrow();
        target.setName(source.getName());
        return PersistenceMapping.tag(rows.save(target));
    }

    public void delete(Tag source) {
        rows.deleteById(source.getId());
    }
}
