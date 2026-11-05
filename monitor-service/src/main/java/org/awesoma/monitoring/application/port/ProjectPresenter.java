package org.awesoma.monitoring.application.port;

import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.ProjectMember;
import org.awesoma.monitoring.application.model.view.ProjectResponse;
import org.awesoma.monitoring.application.model.view.ProjectMemberResponse;
import org.awesoma.monitoring.application.model.UserProfile;
public interface ProjectPresenter {
    ProjectResponse toResponse(Project project);
    ProjectMemberResponse toResponse(ProjectMember member, UserProfile profile);
}
