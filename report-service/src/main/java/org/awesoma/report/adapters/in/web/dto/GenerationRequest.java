package org.awesoma.report.adapters.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import org.awesoma.report.domain.ReportFormat;

@Schema(name = "ReportGenerationRequest")
public record GenerationRequest(
        @Schema(description = "Start of the period with a time zone, inclusive",
                example = "2026-10-01T00:00:00Z") OffsetDateTime from,
        @Schema(description = "End of the period with a time zone, exclusive; not after the request",
                example = "2026-10-02T03:00:00+03:00") OffsetDateTime to,
        @Schema(example = "CSV") ReportFormat format,
        @Schema(description = "Monitors of the project to include; omit for every monitor",
                example = "[11, 12]") List<Long> monitorIds) {
}
