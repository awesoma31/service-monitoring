package org.awesoma.monitoring.web.dto.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;

@ParameterObject
public record PageParams(
        @Schema(description = "Zero-based page index", defaultValue = "0", minimum = "0")
                @Min(0)
                Integer page,
        @Schema(
                        description = "Number of records to return",
                        defaultValue = "20",
                        minimum = "1",
                        maximum = "50")
                @Min(1)
                @Max(MAX_SIZE)
                Integer size) {

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
