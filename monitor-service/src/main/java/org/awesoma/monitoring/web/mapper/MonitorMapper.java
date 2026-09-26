package org.awesoma.monitoring.web.mapper;

import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Tag;
import org.awesoma.monitoring.web.dto.monitor.MonitorResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MonitorMapper {

    @Mapping(target = "projectId", source = "project.id")
    MonitorResponse toResponse(Monitor monitor);

    default String tagName(Tag tag) {
        return tag.getName();
    }
}
