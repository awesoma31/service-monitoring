package org.awesoma.monitoring.web.mapper;

import org.awesoma.monitoring.domain.entity.Tag;
import org.awesoma.monitoring.application.model.view.TagResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TagMapper extends org.awesoma.monitoring.application.port.TagPresenter {

    TagResponse toResponse(Tag tag);
}
