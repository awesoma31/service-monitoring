package org.awesoma.monitoring.adapters.in.web.mapper;

import org.awesoma.monitoring.application.model.view.TagResponse;
import org.awesoma.monitoring.application.port.TagPresenter;
import org.awesoma.monitoring.domain.entity.Tag;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TagMapper extends TagPresenter {

    TagResponse toResponse(Tag tag);
}
