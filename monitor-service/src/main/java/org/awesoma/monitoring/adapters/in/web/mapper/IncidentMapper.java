package org.awesoma.monitoring.adapters.in.web.mapper;

import org.awesoma.monitoring.application.model.view.IncidentResponse;
import org.awesoma.monitoring.application.port.IncidentPresenter;
import org.awesoma.monitoring.domain.entity.Incident;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IncidentMapper extends IncidentPresenter {

    @Mapping(target = "monitorId", source = "monitor.id")
    IncidentResponse toResponse(Incident incident);
}
