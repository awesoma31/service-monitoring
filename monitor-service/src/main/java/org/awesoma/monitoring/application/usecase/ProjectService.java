package org.awesoma.monitoring.application.usecase;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.ProjectMember;
import org.awesoma.monitoring.domain.entity.ProjectMemberId;
import org.awesoma.monitoring.application.event.ProjectDeleted;
import org.awesoma.monitoring.application.port.MonitorRepositoryPort;
import org.awesoma.monitoring.application.port.MemberRepositoryPort;
import org.awesoma.monitoring.application.port.ProjectRepositoryPort;
import org.awesoma.monitoring.application.port.CurrentActor;
import org.awesoma.monitoring.application.port.command.OwnerNotificationsUpdateRequest;
import org.awesoma.monitoring.application.port.command.ProjectCreateRequest;
import org.awesoma.monitoring.application.port.command.ProjectMemberRequest;
import org.awesoma.monitoring.application.model.view.ProjectMemberResponse;
import org.awesoma.monitoring.application.model.view.ProjectResponse;
import org.awesoma.monitoring.application.port.command.ProjectUpdateRequest;
import org.awesoma.monitoring.application.exception.ConflictStateException;
import org.awesoma.monitoring.application.exception.NotFoundException;
import org.awesoma.monitoring.application.port.ProjectPresenter;
import org.awesoma.monitoring.application.port.DomainEvents;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;

@RequiredArgsConstructor
public class ProjectService {
    private final CurrentActor access;

    private final ProjectRepositoryPort projects;
    private final MemberRepositoryPort members;
    private final org.awesoma.monitoring.application.port.UserDirectoryPort users;
    private final ProjectPresenter mapper;
    private final MonitorRepositoryPort monitors;
    private final DomainEvents events;

    public Page<ProjectResponse> list(PageRequest pageable) {
        return (access.supervisor() ? projects.findAll(pageable)
                : projects.findAccessible(access.userId(), pageable)).map(mapper::toResponse);
    }

    public ProjectResponse get(Long id) {
        return mapper.toResponse(require(id));
    }

    /**
     * Creates a project and enrols its owner as a member in one transaction.
     *
     * <p>These two writes must not be separable: a failure between the two inserts would
     * leave a project whose owner is not among its members, a state the API offers no way
     * to notice or repair. It must leave no project at all instead.
     */
    public ProjectResponse create(ProjectCreateRequest request) {
        if (projects.existsBySlug(request.slug())) {
            throw new ConflictStateException("Slug %s is already taken".formatted(request.slug()));
        }
        users.requireActive(request.ownerId());

        Project project = new Project();
        project.setOwnerId(request.ownerId());
        project.setName(request.name());
        project.setSlug(request.slug());
        project.setOwnerNotificationsEnabled(request.ownerNotificationsEnabled());
        project.addMember(request.ownerId());

        return mapper.toResponse(projects.save(project));
    }

    public ProjectResponse update(Long id, ProjectUpdateRequest request) {
        Project project = require(id);
        project.setName(request.name());
        return mapper.toResponse(projects.save(project));
    }

    /**
     * Changes the owner's incident-notification preference without deleting project channels.
     * The controller restricts this preference to the project owner or a supervisor.
     */
    public ProjectResponse updateOwnerNotifications(
            Long id, OwnerNotificationsUpdateRequest request) {
        Project project = require(id);
        project.setOwnerNotificationsEnabled(request.enabled());
        return mapper.toResponse(projects.save(project));
    }

    public void delete(Long id) {
        Project project = projects.findWithLockById(id)
                .orElseThrow(() -> NotFoundException.of("Project", id));
        List<Long> monitorIds = monitors.findIdsByProjectId(id);
        projects.delete(project);
        events.publish(new ProjectDeleted(id, monitorIds));
    }

    public Page<ProjectMemberResponse> listMembers(Long projectId, PageRequest pageable) {
        requireExists(projectId);
        var page = members.findByProjectId(projectId, pageable);
        var profiles = users.profiles(page.getContent().stream().map(ProjectMember::getUserId).distinct().toList());
        return page.map(member -> mapper.toResponse(member, profiles.get(member.getUserId())));
    }

    public ProjectMemberResponse addMember(Long projectId, ProjectMemberRequest request) {
        Project project = require(projectId);
        if (members.existsById(new ProjectMemberId(projectId, request.userId()))) {
            throw new ConflictStateException(
                    "User %d is already a member of project %d".formatted(request.userId(), projectId));
        }
        users.requireActive(request.userId());
        // Flushed right away so that joined_at, set by the insert, is part of the response.
        ProjectMember member = members.saveAndFlush(new ProjectMember(project, request.userId()));
        return mapper.toResponse(member, users.profile(request.userId()));
    }

    public void removeMember(Long projectId, Long userId) {
        Project project = require(projectId);
        if (project.getOwnerId().equals(userId)) {
            throw new ConflictStateException(
                    "The owner of project %d cannot be removed from its members".formatted(projectId));
        }
        members.delete(requireMember(projectId, userId));
    }

    private ProjectMember requireMember(Long projectId, Long userId) {
        return members
                .findById(new ProjectMemberId(projectId, userId))
                .orElseThrow(() -> new NotFoundException(
                        "User %d is not a member of project %d".formatted(userId, projectId)));
    }

    public boolean exists(Long id) {
        return projects.existsById(id);
    }

    /** Existence check that does not load the row, for callers that only need the guard. */
    public void requireExists(Long id) {
        if (!projects.existsById(id)) {
            throw NotFoundException.of("Project", id);
        }
    }

    public Project require(Long id) {
        return projects.findById(id).orElseThrow(() -> NotFoundException.of("Project", id));
    }
}
