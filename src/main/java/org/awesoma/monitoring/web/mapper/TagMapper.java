package org.awesoma.monitoring.web.mapper;

import org.awesoma.monitoring.domain.entity.Tag;
import org.awesoma.monitoring.web.dto.tag.TagResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TagMapper {

    TagResponse toResponse(Tag tag);
}
