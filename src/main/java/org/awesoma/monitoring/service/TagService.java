package org.awesoma.monitoring.service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.domain.entity.Tag;
import org.awesoma.monitoring.repository.TagRepository;
import org.awesoma.monitoring.web.dto.tag.TagCreateRequest;
import org.awesoma.monitoring.web.dto.tag.TagResponse;
import org.awesoma.monitoring.web.exception.ConflictStateException;
import org.awesoma.monitoring.web.exception.NotFoundException;
import org.awesoma.monitoring.web.mapper.TagMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

    private final TagRepository tagRepository;
    private final TagMapper mapper;

    public Page<TagResponse> list(Pageable pageable) {
        return tagRepository.findAll(pageable).map(mapper::toResponse);
    }

    @Transactional
    public TagResponse create(TagCreateRequest request) {
        if (tagRepository.existsByName(request.name())) {
            throw new ConflictStateException("Tag %s already exists".formatted(request.name()));
        }
        Tag tag = new Tag();
        tag.setName(request.name());
        return mapper.toResponse(tagRepository.save(tag));
    }

    @Transactional
    public void delete(Long id) {
        Tag tag = tagRepository.findById(id).orElseThrow(() -> NotFoundException.of("Tag", id));
        tagRepository.delete(tag);
    }

    /**
     * Resolves names to tags, creating the ones that do not exist yet. Tagging a monitor
     * should not force the client to register every label first.
     */
    @Transactional
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
