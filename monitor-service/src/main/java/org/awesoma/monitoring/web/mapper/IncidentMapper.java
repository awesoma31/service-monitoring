package org.awesoma.monitoring.web.mapper;

import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.web.dto.incident.IncidentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IncidentMapper {

    @Mapping(target = "monitorId", source = "monitor.id")
    IncidentResponse toResponse(Incident incident);
}
