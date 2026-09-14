package org.awesoma.monitoring.web.dto.monitor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.awesoma.monitoring.domain.enums.HttpMethod;

/**
 * Bounds mirror the check constraints in the migration: rejecting here gives the client a
 * readable message instead of a database error.
 */
public record MonitorCreateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 2048)
                @Pattern(regexp = "^https?://.+", message = "must be an http or https URL")
                String url,
        HttpMethod httpMethod,
        @Min(10) @Max(86400) int intervalSec,
        @Min(100) @Max(60000) int timeoutMs,
        @Min(100) @Max(599) Integer expectedStatus,
        Set<@NotBlank @Size(max = 50) String> tags) {

    public MonitorCreateRequest {
        httpMethod = httpMethod == null ? HttpMethod.GET : httpMethod;
        expectedStatus = expectedStatus == null ? 200 : expectedStatus;
        tags = tags == null ? Set.of() : tags;
    }
}
