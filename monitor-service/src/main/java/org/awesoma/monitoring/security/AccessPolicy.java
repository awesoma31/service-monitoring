package org.awesoma.monitoring.security;

import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.domain.entity.ProjectMemberId;
import org.awesoma.monitoring.repository.IncidentRepository;
import org.awesoma.monitoring.repository.MonitorRepository;
import org.awesoma.monitoring.repository.ProjectMemberRepository;
import org.awesoma.monitoring.repository.ProjectRepository;
import org.awesoma.monitoring.service.UserDirectory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("access")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessPolicy {
    private final UserDirectory users;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final MonitorRepository monitors;
    private final IncidentRepository incidents;

    public boolean supervisor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SUPERVISOR"));
    }

    public Long userId() {
        return Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    public boolean self(Long id) { return supervisor() || id.equals(userId()); }

    public boolean canCreateProject(Long ownerId) {
        return supervisor() || ownerId.equals(userId());
    }

    public boolean owner(Long projectId) {
        return supervisor() || projects.findById(projectId)
                .map(project -> project.getOwnerId().equals(userId())).orElse(false);
    }

    public boolean project(Long id, boolean write) { return currentResource("project", id, write); }
    public boolean monitor(Long id, boolean write) { return currentResource("monitor", id, write); }
    public boolean incident(Long id, boolean write) { return currentResource("incident", id, write); }

    private boolean currentResource(String resource, Long id, boolean write) {
        return supervisor() || allowed(resource, id, userId(), write);
    }

    public boolean allowed(String resource, Long id, Long userId, boolean write) {
        var user = users.security(userId);
        if (!user.active()) return false;
        if ("SUPERVISOR".equals(user.role())) return true;
        if (write && !"OPERATOR".equals(user.role())) return false;
        Long projectId = switch (resource) {
            case "project" -> id;
            case "monitor" -> monitors.findProjectId(id).orElse(null);
            case "incident" -> incidents.findProjectId(id).orElse(null);
            default -> null;
        };
        return projectId != null && (members.existsById(new ProjectMemberId(projectId, userId))
                || projects.findById(projectId).map(project -> project.getOwnerId().equals(userId)).orElse(false));
    }
}
