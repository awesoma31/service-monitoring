package org.awesoma.monitoring.web.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.List;

/** Documentation model for the RFC 7807 responses produced by GlobalExceptionHandler. */
@Schema(name = "ApiError", description = "RFC 7807 problem detail returned for API errors")
public record ApiErrorResponse(
        @Schema(example = "about:blank") URI type,
        @Schema(example = "Request validation failed") String title,
        @Schema(example = "400") int status,
        @Schema(example = "One or more request values are invalid") String detail,
        @Schema(example = "/api/v1/users") URI instance,
        @Schema(description = "Field-level violations; present for validation errors")
                List<ValidationViolation> violations) {

    public record ValidationViolation(
            @Schema(example = "email") String field,
            @Schema(example = "must be a well-formed email address") String message) {}
}
