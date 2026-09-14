package org.awesoma.monitoring.web.dto.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Pagination parameters shared by every listing endpoint, so the page-size ceiling is
 * defined once rather than repeated per controller. Exceeding it is rejected rather than
 * silently capped: a client asking for 200 rows should learn that it cannot have them.
 */
public record PageParams(@Min(0) Integer page, @Min(1) @Max(MAX_SIZE) Integer size) {

    public static final int MAX_SIZE = 50;
    public static final int DEFAULT_SIZE = 20;

    public PageParams {
        page = page == null ? 0 : page;
        size = size == null ? DEFAULT_SIZE : size;
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size);
    }
}
