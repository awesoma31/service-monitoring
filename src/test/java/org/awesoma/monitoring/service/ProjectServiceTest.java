package org.awesoma.monitoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.ProjectMember;
import org.awesoma.monitoring.domain.entity.User;
import org.awesoma.monitoring.domain.enums.MemberRole;
import org.awesoma.monitoring.repository.ProjectMemberRepository;
import org.awesoma.monitoring.repository.ProjectRepository;
import org.awesoma.monitoring.web.dto.project.ProjectCreateRequest;
import org.awesoma.monitoring.web.dto.project.ProjectMemberRequest;
import org.awesoma.monitoring.web.exception.ConflictStateException;
import org.awesoma.monitoring.web.exception.NotFoundException;
import org.awesoma.monitoring.web.mapper.ProjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
            assertThat(member.getRole()).isEqualTo(MemberRole.OWNER);
            assertThat(member.getUser()).isEqualTo(owner);
        });
    }

    @Test
    void refusesToAddTheSameUserTwice() {
        when(projects.findById(1L)).thenReturn(Optional.of(new Project()));
        when(members.findByProjectIdAndUserId(1L, 2L))
                .thenReturn(Optional.of(new ProjectMember()));

        assertThatThrownBy(() -> service.addMember(1L, new ProjectMemberRequest(2L, MemberRole.VIEWER)))
                .isInstanceOf(ConflictStateException.class);
    }

    @Test
    void refusesToRemoveTheLastOwner() {
        ProjectMember owner = new ProjectMember(new Project(), new User(), MemberRole.OWNER);
        when(members.findByProjectIdAndUserId(1L, 2L)).thenReturn(Optional.of(owner));
        when(members.countByProjectIdAndRole(1L, MemberRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> service.removeMember(1L, 2L))
                .isInstanceOf(ConflictStateException.class)
                .hasMessageContaining("without an owner");

        verify(members, never()).delete(any());
    }

    @Test
    void removesAnOwnerWhenAnotherOneRemains() {
        ProjectMember owner = new ProjectMember(new Project(), new User(), MemberRole.OWNER);
        when(members.findByProjectIdAndUserId(1L, 2L)).thenReturn(Optional.of(owner));
        when(members.countByProjectIdAndRole(1L, MemberRole.OWNER)).thenReturn(2L);

        service.removeMember(1L, 2L);

        verify(members).delete(owner);
    }

    @Test
    void refusesToDemoteTheLastOwner() {
        ProjectMember owner = new ProjectMember(new Project(), new User(), MemberRole.OWNER);
        when(members.findByProjectIdAndUserId(1L, 2L)).thenReturn(Optional.of(owner));
        when(members.countByProjectIdAndRole(1L, MemberRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(1L, 2L, MemberRole.VIEWER))
                .isInstanceOf(ConflictStateException.class);

        assertThat(owner.getRole()).isEqualTo(MemberRole.OWNER);
    }

    @Test
    void reportsAMissingMembershipAsNotFound() {
        when(members.findByProjectIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

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
}
