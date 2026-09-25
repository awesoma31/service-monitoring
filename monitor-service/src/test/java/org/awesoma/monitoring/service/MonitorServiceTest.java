package org.awesoma.monitoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.Tag;
import org.awesoma.monitoring.domain.enums.HttpMethod;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.awesoma.monitoring.repository.MonitorRepository;
import org.awesoma.monitoring.web.dto.monitor.MonitorCreateRequest;
import org.awesoma.monitoring.web.dto.monitor.MonitorUpdateRequest;
import org.awesoma.monitoring.web.exception.ConflictStateException;
import org.awesoma.monitoring.web.exception.NotFoundException;
import org.awesoma.monitoring.web.mapper.MonitorMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.awesoma.monitoring.integration.MonitorDeleted;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    @Mock private MonitorRepository monitors;
    @Mock private ProjectService projects;
    @Mock private TagService tags;
    @Mock private MonitorMapper mapper;
    @Mock private ApplicationEventPublisher events;
    @InjectMocks private MonitorService service;

    @Test
    void refusesTwoMonitorsWithTheSameNameInOneProject() {
        when(projects.require(1L)).thenReturn(new Project());
        when(monitors.existsByProjectIdAndName(1L, "Home")).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, request("Home")))
                .isInstanceOf(ConflictStateException.class)
                .hasMessageContaining("Home");

        verify(monitors, never()).save(any());
    }

    @Test
    void createAppliesTheRequestAndResolvesTags() {
        Tag tag = new Tag();
        tag.setName("prod");
        when(projects.require(1L)).thenReturn(new Project());
        when(monitors.existsByProjectIdAndName(1L, "Home")).thenReturn(false);
        when(tags.resolveOrCreate(Set.of("prod"))).thenReturn(Set.of(tag));
        when(monitors.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(1L, request("Home"));

        ArgumentCaptor<Monitor> saved = ArgumentCaptor.forClass(Monitor.class);
        verify(monitors).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Home");
        assertThat(saved.getValue().getUrl()).isEqualTo("https://example.com");
        assertThat(saved.getValue().getIntervalSec()).isEqualTo(60);
        assertThat(saved.getValue().getTags()).containsExactly(tag);
    }

    @Test
    void updateOverwritesEveryMutableField() {
        Monitor monitor = new Monitor();
        monitor.setName("Old");
        monitor.setActive(true);
        when(monitors.findById(5L)).thenReturn(Optional.of(monitor));

        service.update(
                5L, new MonitorUpdateRequest("New", "https://new.example", HttpMethod.HEAD, 120, 3000, 204, false));

        assertThat(monitor.getName()).isEqualTo("New");
        assertThat(monitor.getUrl()).isEqualTo("https://new.example");
        assertThat(monitor.getHttpMethod()).isEqualTo(HttpMethod.HEAD);
        assertThat(monitor.getIntervalSec()).isEqualTo(120);
        assertThat(monitor.getExpectedStatus()).isEqualTo(204);
        assertThat(monitor.isActive()).isFalse();
        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.PAUSED);
    }

    @Test
    void enablingAPausedMonitorReturnsItToUnknown() {
        Monitor monitor = new Monitor();
        monitor.setCurrentState(MonitorState.PAUSED);
        monitor.setActive(false);
        when(monitors.findById(5L)).thenReturn(Optional.of(monitor));

        service.update(
                5L,
                new MonitorUpdateRequest(
                        "Enabled", "https://enabled.example", HttpMethod.GET, 60, 5000, 200, true));

        assertThat(monitor.isActive()).isTrue();
        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.UNKNOWN);
    }

    @Test
    void replaceTagsSwapsTheWholeSet() {
        Monitor monitor = new Monitor();
        Tag old = new Tag();
        old.setName("old");
        monitor.getTags().add(old);
        Tag fresh = new Tag();
        fresh.setName("fresh");
        when(monitors.findById(5L)).thenReturn(Optional.of(monitor));
        when(tags.resolveOrCreate(anySet())).thenReturn(Set.of(fresh));

        service.replaceTags(5L, Set.of("fresh"));

        assertThat(monitor.getTags()).containsExactly(fresh);
    }

    @Test
    void listingWithoutATagAsksForTheWholeProject() {
        when(monitors.findByProjectId(eq(1L), any())).thenReturn(Page.empty());

        service.listByProject(1L, null, Pageable.unpaged());

        verify(monitors).findByProjectId(eq(1L), any());
        verify(monitors, never()).findByProjectIdAndTagsName(any(), any(), any());
    }

    @Test
    void listingWithATagNarrowsTheQuery() {
        when(monitors.findByProjectIdAndTagsName(eq(1L), eq("prod"), any())).thenReturn(Page.empty());

        service.listByProject(1L, "prod", Pageable.unpaged());

        verify(monitors).findByProjectIdAndTagsName(eq(1L), eq("prod"), any());
        verify(monitors, never()).findByProjectId(any(), any());
    }

    @Test
    void aBlankTagIsTreatedAsNoFilter() {
        when(monitors.findByProjectId(eq(1L), any())).thenReturn(Page.empty());

        service.listByProject(1L, "  ", Pageable.unpaged());

        verify(monitors).findByProjectId(eq(1L), any());
    }

    @Test
    void reportsAMissingMonitorAsNotFound() {
        when(monitors.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRemovesAnExistingMonitor() {
        Monitor monitor = new Monitor();
        when(monitors.findById(5L)).thenReturn(Optional.of(monitor));

        service.delete(5L);

        verify(monitors).delete(monitor);
        verify(events).publishEvent(new MonitorDeleted(5L));
    }

    private MonitorCreateRequest request(String name) {
        return new MonitorCreateRequest(
                name, "https://example.com", null, 60, 5000, null, Set.of("prod"));
    }
}
