package org.awesoma.monitoring.web.mapper;

import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.entity.Notification;
import org.awesoma.monitoring.web.dto.incident.IncidentResponse;
import org.awesoma.monitoring.web.dto.incident.NotificationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IncidentMapper {

    @Mapping(target = "monitorId", source = "monitor.id")
    IncidentResponse toResponse(Incident incident);

    @Mapping(target = "incidentId", source = "incident.id")
    @Mapping(target = "channelId", source = "channel.id")
    @Mapping(target = "channelType", source = "channel.type")
    @Mapping(target = "target", source = "channel.target")
    NotificationResponse toResponse(Notification notification);
}
