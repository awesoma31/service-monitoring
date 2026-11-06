package org.awesoma.report.application.usecase;

import org.awesoma.report.application.port.AccessLevel;
import org.awesoma.report.application.port.ProjectAccessPort;

final class ProjectAccessCheck {

    private ProjectAccessCheck() {
    }

    /** An unavailable check propagates as it is: access is refused, never assumed. */
    static void require(ProjectAccessPort access, long projectId, long userId, AccessLevel level) {
        switch (access.check(projectId, userId, level)) {
            case ALLOWED -> { }
            case DENIED -> throw new ProjectAccessDeniedException();
            case PROJECT_NOT_FOUND -> throw new ReportNotFoundException("Project " + projectId + " not found");
        }
    }
}
