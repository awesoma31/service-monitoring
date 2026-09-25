package org.awesoma.monitoring.web.mapper;

import org.awesoma.monitoring.domain.entity.Channel;
import org.awesoma.monitoring.web.dto.channel.ChannelResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ChannelMapper {

    @Mapping(target = "projectId", source = "project.id")
    ChannelResponse toResponse(Channel channel);
}
