package org.awesoma.monitoring.adapters.in.web.mapper;

import org.awesoma.monitoring.application.model.view.MonitorResponse;
import org.awesoma.monitoring.application.port.MonitorPresenter;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Tag;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MonitorMapper extends MonitorPresenter {

    @Mapping(target = "projectId", source = "project.id")
    MonitorResponse toResponse(Monitor monitor);

    default String tagName(Tag tag) {
        return tag.getName();
    }
}
