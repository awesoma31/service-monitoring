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

    @Mapping(target = "ownerId", source = "owner.id")
    ProjectResponse toResponse(Project project);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "fullName", source = "user.fullName")
    ProjectMemberResponse toResponse(ProjectMember member);
}
