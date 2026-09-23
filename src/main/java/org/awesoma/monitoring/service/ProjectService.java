package org.awesoma.monitoring.service;

import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.ProjectMember;
import org.awesoma.monitoring.domain.entity.User;
import org.awesoma.monitoring.domain.enums.MemberRole;
import org.awesoma.monitoring.repository.ProjectMemberRepository;
import org.awesoma.monitoring.repository.ProjectRepository;
import org.awesoma.monitoring.web.dto.project.ProjectCreateRequest;
import org.awesoma.monitoring.web.dto.project.ProjectMemberRequest;
import org.awesoma.monitoring.web.dto.project.ProjectMemberResponse;
import org.awesoma.monitoring.web.dto.project.ProjectResponse;
import org.awesoma.monitoring.web.dto.project.ProjectUpdateRequest;
import org.awesoma.monitoring.web.exception.ConflictStateException;
import org.awesoma.monitoring.web.exception.NotFoundException;
import org.awesoma.monitoring.web.mapper.ProjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final UserService users;
    private final ProjectMapper mapper;

    public Page<ProjectResponse> list(Pageable pageable) {
        return projects.findAll(pageable).map(mapper::toResponse);
    }

    public ProjectResponse get(Long id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public ProjectResponse create(ProjectCreateRequest request) {
        if (projects.existsBySlug(request.slug())) {
            throw new ConflictStateException("Slug %s is already taken".formatted(request.slug()));
        }
        User owner = users.require(request.ownerId());

        Project project = new Project();
        project.setOwner(owner);
        project.setName(request.name());
        project.setSlug(request.slug());
        project.addMember(owner, MemberRole.OWNER);

        return mapper.toResponse(projects.save(project));
    }

    @Transactional
    public ProjectResponse update(Long id, ProjectUpdateRequest request) {
        Project project = require(id);
        project.setName(request.name());
        return mapper.toResponse(project);
    }

    @Transactional
    public void delete(Long id) {
        projects.delete(require(id));
    }

    public Page<ProjectMemberResponse> listMembers(Long projectId, Pageable pageable) {
        requireExists(projectId);
        return members.findByProjectId(projectId, pageable).map(mapper::toResponse);
    }

    @Transactional
    public ProjectMemberResponse addMember(Long projectId, ProjectMemberRequest request) {
        Project project = require(projectId);
        if (members.findByProjectIdAndUserId(projectId, request.userId()).isPresent()) {
            throw new ConflictStateException(
                    "User %d is already a member of project %d".formatted(request.userId(), projectId));
        }
        User user = users.require(request.userId());
        return mapper.toResponse(members.save(new ProjectMember(project, user, request.role())));
    }

    @Transactional
    public ProjectMemberResponse changeRole(Long projectId, Long userId, MemberRole role) {
        ProjectMember member = requireMember(projectId, userId);
        if (member.getRole() == MemberRole.OWNER && role != MemberRole.OWNER) {
            requireAnotherOwnerExists(projectId);
        }
        member.setRole(role);
        return mapper.toResponse(member);
    }

    @Transactional
    public void removeMember(Long projectId, Long userId) {
        ProjectMember member = requireMember(projectId, userId);
        if (member.getRole() == MemberRole.OWNER) {
            requireAnotherOwnerExists(projectId);
        }
        members.delete(member);
    }

    private void requireAnotherOwnerExists(Long projectId) {
        if (members.countByProjectIdAndRole(projectId, MemberRole.OWNER) <= 1) {
            throw new ConflictStateException(
                    "Project %d would be left without an owner".formatted(projectId));
        }
    }

    private ProjectMember requireMember(Long projectId, Long userId) {
        return members
                .findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "User %d is not a member of project %d".formatted(userId, projectId)));
    }

    public void requireExists(Long id) {
        if (!projects.existsById(id)) {
            throw NotFoundException.of("Project", id);
        }
    }

    public Project require(Long id) {
        return projects.findById(id).orElseThrow(() -> NotFoundException.of("Project", id));
    }
}
