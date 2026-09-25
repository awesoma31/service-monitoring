package org.awesoma.monitoring.service;

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
import org.awesoma.monitoring.domain.entity.User;
import org.awesoma.monitoring.repository.ProjectMemberRepository;
import org.awesoma.monitoring.repository.ProjectRepository;
import org.awesoma.monitoring.web.dto.project.ProjectCreateRequest;
import org.awesoma.monitoring.web.dto.project.ProjectMemberRequest;
import org.awesoma.monitoring.web.dto.project.ProjectResponse;
import org.awesoma.monitoring.web.dto.project.ProjectUpdateRequest;
import org.awesoma.monitoring.web.exception.ConflictStateException;
import org.awesoma.monitoring.web.exception.NotFoundException;
import org.awesoma.monitoring.web.mapper.ProjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock private ProjectRepository projects;
    @Mock private ProjectMemberRepository members;
    @Mock private UserService users;
    @Mock private ProjectMapper mapper;
    @InjectMocks private ProjectService service;

    @Test
    void rejectsASlugThatIsAlreadyTaken() {
        when(projects.existsBySlug("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new ProjectCreateRequest(1L, "Name", "taken")))
                .isInstanceOf(ConflictStateException.class);

        verify(projects, never()).save(any());
    }

    @Test
    void createEnrolsTheOwnerAsAMemberInTheSameSave() {
        User owner = new User();
        owner.setId(7L);
        when(projects.existsBySlug("fresh")).thenReturn(false);
        when(users.require(7L)).thenReturn(owner);
        when(projects.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(new ProjectCreateRequest(7L, "Name", "fresh"));

        ArgumentCaptor<Project> saved = ArgumentCaptor.forClass(Project.class);
        verify(projects).save(saved.capture());
        assertThat(saved.getValue().getMembers()).singleElement().satisfies(member -> {
            assertThat(member.getUser()).isEqualTo(owner);
        });
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
        when(members.findById(new ProjectMemberId(1L, 2L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeMember(1L, 2L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    void listingMembersOfAMissingProjectFails() {
        when(projects.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.listMembers(9L, org.springframework.data.domain.Pageable.unpaged()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listMapsEveryProjectFromTheRequestedPage() {
        Project first = new Project();
        Project second = new Project();
        ProjectResponse firstResponse = new ProjectResponse(1L, 10L, "First", "first", null);
        ProjectResponse secondResponse = new ProjectResponse(2L, 10L, "Second", "second", null);
        PageRequest pageable = PageRequest.of(1, 2);
        when(projects.findAll(pageable)).thenReturn(new PageImpl<>(List.of(first, second), pageable, 4));
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
    void addMemberLinksTheUserToTheProject() {
        Project project = new Project();
        User user = new User();
        when(projects.findById(1L)).thenReturn(Optional.of(project));
        when(members.existsById(new ProjectMemberId(1L, 2L))).thenReturn(false);
        when(users.require(2L)).thenReturn(user);
        when(members.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.addMember(1L, new ProjectMemberRequest(2L));

        ArgumentCaptor<ProjectMember> saved = ArgumentCaptor.forClass(ProjectMember.class);
        verify(members).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getProject()).isEqualTo(project);
        assertThat(saved.getValue().getUser()).isEqualTo(user);
    }

}
