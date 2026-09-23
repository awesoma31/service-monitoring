package org.awesoma.monitoring.web.dto.monitor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import org.awesoma.monitoring.domain.enums.HttpMethod;

public record MonitorCreateRequest(
        @Schema(example = "Public API") @NotBlank @Size(max = 255) String name,
        @Schema(example = "https://api.example.com/health")
                @NotBlank
                @Size(max = 2048)
                @Pattern(regexp = "^https?://.+", message = "must be an http or https URL")
                String url,
        @Schema(example = "GET", defaultValue = "GET") HttpMethod httpMethod,
        @Schema(example = "60", minimum = "10", maximum = "86400")
                @Min(10)
                @Max(86400)
                int intervalSec,
        @Schema(example = "5000", minimum = "100", maximum = "60000")
                @Min(100)
                @Max(60000)
                int timeoutMs,
        @Schema(example = "200", defaultValue = "200", minimum = "100", maximum = "599")
                @Min(100)
                @Max(599)
                Integer expectedStatus,
        @Schema(example = "[\"production\", \"api\"]")
                Set<@NotBlank @Size(max = 50) String> tags) {

    public MonitorCreateRequest {
        httpMethod = httpMethod == null ? HttpMethod.GET : httpMethod;
        expectedStatus = expectedStatus == null ? 200 : expectedStatus;
        tags = tags == null ? Set.of() : tags;
    }
}
