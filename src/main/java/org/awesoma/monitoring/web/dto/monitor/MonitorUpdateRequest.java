package org.awesoma.monitoring.web.dto.monitor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.awesoma.monitoring.domain.enums.HttpMethod;

/** The owning project is fixed; a monitor does not move between projects. */
public record MonitorUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 2048)
                @Pattern(regexp = "^https?://.+", message = "must be an http or https URL")
                String url,
        @NotNull HttpMethod httpMethod,
        @Min(10) @Max(86400) int intervalSec,
        @Min(100) @Max(60000) int timeoutMs,
        @Min(100) @Max(599) int expectedStatus,
        boolean active) {
}
