package org.awesoma.report.adapters.out.http;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.report.application.port.AccessDecision;
import org.awesoma.report.application.port.AccessLevel;
import org.awesoma.report.application.port.AccessUnavailableException;
import org.awesoma.report.application.port.ProjectAccessPort;
import org.awesoma.security.access.ResourceAccessClient;
import org.springframework.stereotype.Component;

/**
 * Asks monitor-service, which also checks with user-service that the user is still active.
 * Existence is asked first and separately: the access check lets a supervisor through even
 * for a project that no longer exists. Any failure closes access rather than guessing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorServiceProjectAccess implements ProjectAccessPort {
    private final ProjectLookupClient projects;
    private final ResourceAccessClient access;

    @Override
    public AccessDecision check(long projectId, long userId, AccessLevel level) {
        try {
            if (!projects.projectExists(projectId)) return AccessDecision.PROJECT_NOT_FOUND;
            return access.allowed("project", projectId, userId, level == AccessLevel.WRITE)
                    ? AccessDecision.ALLOWED : AccessDecision.DENIED;
        } catch (RuntimeException error) {
            log.warn("Project access cannot be verified: project_id={}, user_id={}", projectId, userId, error);
            throw new AccessUnavailableException("Project access cannot be verified", error);
        }
    }
}
