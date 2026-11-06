package org.awesoma.monitoring.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.ProjectMember;
import org.awesoma.monitoring.domain.entity.ProjectMemberId;
import org.awesoma.monitoring.application.event.ProjectDeleted;
import org.awesoma.monitoring.application.port.MonitorRepositoryPort;
import org.awesoma.monitoring.application.port.MemberRepositoryPort;
import org.awesoma.monitoring.application.port.ProjectRepositoryPort;
import org.awesoma.monitoring.application.port.CurrentActor;
import org.awesoma.monitoring.adapters.in.web.dto.project.OwnerNotificationsUpdateRequest;
import org.awesoma.monitoring.adapters.in.web.dto.project.ProjectCreateRequest;
import org.awesoma.monitoring.adapters.in.web.dto.project.ProjectMemberRequest;
import org.awesoma.monitoring.application.model.view.ProjectResponse;
import org.awesoma.monitoring.adapters.in.web.dto.project.ProjectUpdateRequest;
import org.awesoma.monitoring.application.exception.ConflictStateException;
import org.awesoma.monitoring.application.exception.NotFoundException;
import org.awesoma.monitoring.adapters.in.web.mapper.ProjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.awesoma.monitoring.application.port.DomainEvents;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;
import org.awesoma.monitoring.application.model.PageRequest;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock private ProjectRepositoryPort projects;
    @Mock private MemberRepositoryPort members;
    @Mock private org.awesoma.monitoring.application.port.UserDirectoryPort users;
    @Mock private ProjectMapper mapper;
    @Mock private DomainEvents events;
    @Mock private MonitorRepositoryPort monitorRepository;
    @Mock private CurrentActor access;
    @InjectMocks private ProjectService service;

    @Test
    void rejectsASlugThatIsAlreadyTaken() {
        when(projects.existsBySlug("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new ProjectCreateRequest(1L, "Name", "taken", true)))
                .isInstanceOf(ConflictStateException.class);

        verify(projects, never()).save(any());
    }

    @Test
    void createEnrolsTheOwnerAsAMemberInTheSameSave() {
        Long owner = 7L;
        when(projects.existsBySlug("fresh")).thenReturn(false);
        when(projects.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(new ProjectCreateRequest(7L, "Name", "fresh", null));

        ArgumentCaptor<Project> saved = ArgumentCaptor.forClass(Project.class);
        verify(projects).save(saved.capture());
        assertThat(saved.getValue().isOwnerNotificationsEnabled()).isTrue();
        assertThat(saved.getValue().getMembers()).singleElement().satisfies(member -> {
            assertThat(member.getUserId()).isEqualTo(owner);
        });
    }

    @Test
    void createCanDisableOwnerIncidentNotifications() {
        Long owner = 7L;
        when(projects.existsBySlug("silent")).thenReturn(false);
        when(projects.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(new ProjectCreateRequest(7L, "Name", "silent", false));

        ArgumentCaptor<Project> saved = ArgumentCaptor.forClass(Project.class);
        verify(projects).save(saved.capture());
        assertThat(saved.getValue().isOwnerNotificationsEnabled()).isFalse();
    }

    @Test
    void refusesToAddTheSameUserTwice() {
        when(projects.findById(1L)).thenReturn(Optional.of(new Project()));
        when(members.existsById(new ProjectMemberId(1L, 2L))).thenReturn(true);

        assertThatThrownBy(() -> service.addMember(1L, new ProjectMemberRequest(2L)))
                .isInstanceOf(ConflictStateException.class);
    }

    @Test
    void reportsAMissingMembershipAsNotFound() {
        Project project = projectWithOwner(9L);
        when(projects.findById(1L)).thenReturn(Optional.of(project));
        when(members.findById(new ProjectMemberId(1L, 2L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeMember(1L, 2L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    void refusesToRemoveTheProjectOwnerFromMembers() {
        when(projects.findById(1L)).thenReturn(Optional.of(projectWithOwner(2L)));

        assertThatThrownBy(() -> service.removeMember(1L, 2L))
                .isInstanceOf(ConflictStateException.class)
                .hasMessageContaining("owner");

        verify(members, never()).delete(any());
    }

    @Test
    void listingMembersOfAMissingProjectFails() {
        when(projects.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.listMembers(9L, new PageRequest(0, 50)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listMapsEveryProjectFromTheRequestedPage() {
        when(access.supervisor()).thenReturn(true);
        Project first = new Project();
        Project second = new Project();
        ProjectResponse firstResponse = new ProjectResponse(1L, 10L, "First", "first", null, true);
        ProjectResponse secondResponse = new ProjectResponse(2L, 10L, "Second", "second", null, true);
        PageRequest pageable = PageRequest.of(1, 2);
        when(projects.findAll(pageable)).thenReturn(new Page<>(List.of(first, second), pageable.page(), pageable.size(), 4));
        when(mapper.toResponse(first)).thenReturn(firstResponse);
        when(mapper.toResponse(second)).thenReturn(secondResponse);

        var result = service.list(pageable);

        assertThat(result.getContent()).containsExactly(firstResponse, secondResponse);
        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    @Test
    void updateChangesOnlyTheProjectName() {
        Project project = new Project();
        project.setName("Old");
        project.setSlug("stable-slug");
        when(projects.findById(3L)).thenReturn(Optional.of(project));

        service.update(3L, new ProjectUpdateRequest("New"));

        assertThat(project.getName()).isEqualTo("New");
        assertThat(project.getSlug()).isEqualTo("stable-slug");
    }

    @Test
    void ownerCanDisableIncidentNotificationsForTheProject() {
        Project project = new Project();
        when(projects.findById(3L)).thenReturn(Optional.of(project));

        service.updateOwnerNotifications(3L, new OwnerNotificationsUpdateRequest(false));

        assertThat(project.isOwnerNotificationsEnabled()).isFalse();
    }

    @Test
    void addMemberLinksTheUserToTheProject() {
        Project project = new Project();
        Long user = 2L;
        when(projects.findById(1L)).thenReturn(Optional.of(project));
        when(members.existsById(new ProjectMemberId(1L, 2L))).thenReturn(false);
        when(members.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.addMember(1L, new ProjectMemberRequest(2L));

        ArgumentCaptor<ProjectMember> saved = ArgumentCaptor.forClass(ProjectMember.class);
        verify(members).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getProject()).isEqualTo(project);
        assertThat(saved.getValue().getUserId()).isEqualTo(user);
    }

    @Test
    void deletingAProjectAnnouncesItWithItsMonitors() {
        Project project = new Project();
        when(projects.findWithLockById(4L)).thenReturn(Optional.of(project));
        when(monitorRepository.findIdsByProjectId(4L)).thenReturn(List.of(10L, 11L));

        service.delete(4L);

        verify(projects).delete(project);
        verify(events).publish(new ProjectDeleted(4L, List.of(10L, 11L)));
    }

    private Project projectWithOwner(long ownerId) {
        Long owner = ownerId;
        Project project = new Project();
        project.setOwnerId(owner);
        return project;
    }
}
