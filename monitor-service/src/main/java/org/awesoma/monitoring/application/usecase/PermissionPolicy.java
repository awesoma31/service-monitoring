package org.awesoma.monitoring.application.usecase;

import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.application.port.IncidentRepositoryPort;
import org.awesoma.monitoring.application.port.MemberRepositoryPort;
import org.awesoma.monitoring.application.port.MonitorRepositoryPort;
import org.awesoma.monitoring.application.port.ProjectRepositoryPort;
import org.awesoma.monitoring.application.port.UserDirectoryPort;
import org.awesoma.monitoring.domain.entity.ProjectMemberId;

/** Authorization rules over application ports, independent of the security transport. */
@RequiredArgsConstructor
public class PermissionPolicy {
    private final UserDirectoryPort users;
    private final ProjectRepositoryPort projects;
    private final MemberRepositoryPort members;
    private final MonitorRepositoryPort monitors;
    private final IncidentRepositoryPort incidents;

    public boolean owner(Long projectId, Long userId) {
        return projects.findById(projectId).map(project -> project.getOwnerId().equals(userId)).orElse(false);
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
                || owner(projectId, userId));
    }
}
