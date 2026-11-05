package org.awesoma.monitoring.application.port;

import org.awesoma.monitoring.domain.entity.Tag;
import org.awesoma.monitoring.application.model.view.TagResponse;
public interface TagPresenter {
    TagResponse toResponse(Tag tag);
}
