package org.awesoma.monitoring.application.usecase;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.domain.entity.Tag;
import org.awesoma.monitoring.application.port.TagRepositoryPort;
import org.awesoma.monitoring.application.port.command.TagCreateRequest;
import org.awesoma.monitoring.application.model.view.TagResponse;
import org.awesoma.monitoring.application.exception.ConflictStateException;
import org.awesoma.monitoring.application.exception.NotFoundException;
import org.awesoma.monitoring.application.port.TagPresenter;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;

@RequiredArgsConstructor
public class TagService {

    private final TagRepositoryPort tagRepository;
    private final TagPresenter mapper;

    public Page<TagResponse> list(PageRequest pageable) {
        return tagRepository.findAll(pageable).map(mapper::toResponse);
    }

    public TagResponse create(TagCreateRequest request) {
        if (tagRepository.existsByName(request.name())) {
            throw new ConflictStateException("Tag %s already exists".formatted(request.name()));
        }
        Tag tag = new Tag();
        tag.setName(request.name());
        return mapper.toResponse(tagRepository.save(tag));
    }

    public void delete(Long id) {
        Tag tag = tagRepository.findById(id).orElseThrow(() -> NotFoundException.of("Tag", id));
        tagRepository.delete(tag);
    }

    /**
     * Resolves names to tags, creating the ones that do not exist yet. Tagging a monitor
     * should not force the client to register every label first.
     */
    public Set<Tag> resolveOrCreate(Set<String> names) {
        if (names.isEmpty()) {
            return new HashSet<>();
        }
        Map<String, Tag> existing = tagRepository.findByNameIn(names).stream()
                .collect(Collectors.toMap(Tag::getName, Function.identity()));

        Set<Tag> resolved = new HashSet<>(existing.values());
        names.stream()
                .filter(name -> !existing.containsKey(name))
                .forEach(name -> {
                    Tag tag = new Tag();
                    tag.setName(name);
                    resolved.add(tagRepository.save(tag));
                });
        return resolved;
    }
}
