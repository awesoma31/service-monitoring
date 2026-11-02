package org.awesoma.report.adapters.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.awesoma.report.domain.FailureCode;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportFormat;
import org.awesoma.report.domain.ReportSource;
import org.awesoma.report.domain.ReportStatus;

/**
 * Report metadata. File fields are {@code null} until the report is READY; the internal
 * object key and storage credentials are never part of it.
 */
@Schema(name = "Report")
public record ReportResponse(
        UUID id,
        long projectId,
        ReportSource source,
        long createdBy,
        ReportFormat format,
        ReportStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Schema(description = "Present once READY", example = "uptime-october.pdf") String fileName,
        @Schema(description = "Present once READY", example = "application/pdf") String contentType,
        @Schema(description = "Present once READY", example = "48213") Long sizeBytes,
        @Schema(description = "SHA-256 of the stored bytes in hex; present once READY") String sha256,
        @Schema(description = "Safe reason of a FAILED report") FailureCode failureCode,
        @Schema(description = "Present for generated reports") Generation generation) {

    public static ReportResponse of(Report report) {
        boolean ready = report.contentAvailable();
        return new ReportResponse(report.id(), report.projectId(), report.source(), report.createdBy(),
                report.format(), report.status(), report.createdAt(), report.updatedAt(),
                ready ? report.fileName().value() : null, ready ? report.format().contentType() : null,
                ready ? report.sizeBytes() : null, ready ? report.sha256() : null, report.failure(),
                Generation.of(report));
    }

    @Schema(name = "ReportGeneration")
    public record Generation(
            @Schema(description = "Start of the UTC period, inclusive") Instant from,
            @Schema(description = "End of the UTC period, exclusive") Instant to,
            @Schema(description = "Selected monitors; null means every monitor of the project")
                    List<Long> monitorIds,
            Instant requestedAt,
            Instant generatedAt,
            int attempts,
            Instant nextAttemptAt,
            @Schema(description = "Where the sources stopped reading for the latest attempt") SourceBounds sourceBounds) {

        static Generation of(Report report) {
            var parameters = report.generation();
            if (parameters == null) return null;
            var bounds = report.readBounds();
            return new Generation(parameters.period().from(), parameters.period().to(),
                    parameters.selection().all() ? null : parameters.selection().monitorIds(),
                    parameters.requestedAt(), report.generatedAt(), report.attempts(), report.nextAttemptAt(),
                    bounds == null ? null : new SourceBounds(bounds.observedAt(), bounds.checksUpperId(),
                            bounds.incidentsUpperId()));
        }
    }

    @Schema(name = "ReportSourceBounds")
    public record SourceBounds(Instant observedAt, Long checksUpperId, Long incidentsUpperId) {
    }
}
