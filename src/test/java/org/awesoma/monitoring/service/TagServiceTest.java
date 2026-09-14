package org.awesoma.monitoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.awesoma.monitoring.domain.entity.Tag;
import org.awesoma.monitoring.repository.TagRepository;
import org.awesoma.monitoring.web.dto.tag.TagCreateRequest;
import org.awesoma.monitoring.web.exception.ConflictStateException;
import org.awesoma.monitoring.web.mapper.TagMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock private TagRepository tags;
    @Mock private TagMapper mapper;
    @InjectMocks private TagService service;

    @Test
    void refusesADuplicateName() {
        when(tags.existsByName("prod")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new TagCreateRequest("prod")))
                .isInstanceOf(ConflictStateException.class);

        verify(tags, never()).save(any());
    }

    @Test
    void resolveReturnsEmptyWithoutTouchingTheRepository() {
        assertThat(service.resolveOrCreate(Set.of())).isEmpty();

        verify(tags, never()).findByNameIn(any());
    }

    @Test
    void resolveCreatesOnlyTheNamesThatAreMissing() {
        Tag existing = new Tag();
        existing.setName("known");
        when(tags.findByNameIn(Set.of("known", "new"))).thenReturn(List.of(existing));
        when(tags.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Set<Tag> resolved = service.resolveOrCreate(Set.of("known", "new"));

        assertThat(resolved).extracting(Tag::getName).containsExactlyInAnyOrder("known", "new");
        verify(tags).save(any());
    }
}
