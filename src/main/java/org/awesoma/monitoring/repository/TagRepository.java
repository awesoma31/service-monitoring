package org.awesoma.monitoring.repository;

import java.util.Collection;
import java.util.List;
import org.awesoma.monitoring.domain.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {


    List<Tag> findByNameIn(Collection<String> names);

    boolean existsByName(String name);
}
