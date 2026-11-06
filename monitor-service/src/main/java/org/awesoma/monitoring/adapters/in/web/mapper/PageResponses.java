package org.awesoma.monitoring.adapters.in.web.mapper;

import org.awesoma.monitoring.application.model.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** Renders an application page in the JSON shape the API has always returned. */
public final class PageResponses {
    private PageResponses() {
    }

    public static <T> org.springframework.data.domain.Page<T> of(Page<T> page) {
        return new PageImpl<>(page.content(), PageRequest.of(page.page(), page.size()), page.totalElements());
    }
}
