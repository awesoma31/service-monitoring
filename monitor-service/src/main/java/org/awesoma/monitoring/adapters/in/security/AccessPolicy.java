package org.awesoma.monitoring.adapters.in.security;

import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.application.port.CurrentActor;
import org.awesoma.monitoring.application.usecase.PermissionPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("access")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessPolicy implements CurrentActor {
    private final PermissionPolicy permissions;

    @Override
    public boolean supervisor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SUPERVISOR"));
    }

    @Override
    public Long userId() {
        return Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    public boolean self(Long id) { return supervisor() || id.equals(userId()); }

    public boolean canCreateProject(Long ownerId) {
        return supervisor() || ownerId.equals(userId());
    }

    public boolean owner(Long projectId) {
        return supervisor() || permissions.owner(projectId, userId());
    }

    public boolean project(Long id, boolean write) { return currentResource("project", id, write); }
    public boolean monitor(Long id, boolean write) { return currentResource("monitor", id, write); }
    public boolean incident(Long id, boolean write) { return currentResource("incident", id, write); }

    private boolean currentResource(String resource, Long id, boolean write) {
        return supervisor() || allowed(resource, id, userId(), write);
    }

    public boolean allowed(String resource, Long id, Long userId, boolean write) {
        return permissions.allowed(resource, id, userId, write);
    }
}
