package org.awesoma.monitoring.web.mapper;

import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.ProjectMember;
import org.awesoma.monitoring.web.dto.project.ProjectMemberResponse;
import org.awesoma.monitoring.web.dto.project.ProjectResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProjectMapper {

    ProjectResponse toResponse(Project project);

    default ProjectMemberResponse toResponse(ProjectMember member, org.awesoma.security.UserProfile profile) {
        return new ProjectMemberResponse(member.getUserId(), profile.email(), profile.fullName(), member.getJoinedAt());
    }
}
