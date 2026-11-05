package org.awesoma.monitoring.web.mapper;

import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.ProjectMember;
import org.awesoma.monitoring.application.model.view.ProjectMemberResponse;
import org.awesoma.monitoring.application.model.view.ProjectResponse;
import org.awesoma.monitoring.application.model.UserProfile;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProjectMapper extends org.awesoma.monitoring.application.port.ProjectPresenter {

    ProjectResponse toResponse(Project project);

    default ProjectMemberResponse toResponse(ProjectMember member, UserProfile profile) {
        return new ProjectMemberResponse(member.getUserId(), profile.email(), profile.fullName(), member.getJoinedAt());
    }
}
