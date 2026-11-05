package org.awesoma.report.adapters.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.report.adapters.in.web.dto.GenerationRequest;
import org.awesoma.report.adapters.in.web.dto.ReportResponse;
import org.awesoma.report.application.usecase.GenerateReportCommand;
import org.awesoma.report.application.usecase.ListReportsQuery;
import org.awesoma.report.application.usecase.ReportUseCases.DeleteReport;
import org.awesoma.report.application.usecase.ReportUseCases.DownloadReport;
import org.awesoma.report.application.usecase.ReportUseCases.GenerateReport;
import org.awesoma.report.application.usecase.ReportUseCases.GetReportMetadata;
import org.awesoma.report.application.usecase.ReportUseCases.ListReports;
import org.awesoma.report.application.usecase.ReportUseCases.UploadReport;
import org.awesoma.report.application.usecase.UploadReportCommand;
import org.awesoma.report.domain.ReportSource;
import org.awesoma.report.domain.ReportStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Stored report files of a project")
public class ReportController {
    static final String TOTAL_COUNT_HEADER = "X-Total-Count";
    static final String HAS_NEXT_HEADER = "X-Has-Next";

    private final UploadReport uploads;
    private final GetReportMetadata metadata;
    private final DownloadReport downloads;
    private final ListReports lists;
    private final DeleteReport deletions;
    private final GenerateReport generations;

    @Operation(summary = "Upload a finished CSV or PDF report",
            responses = {
                @ApiResponse(responseCode = "201", description = "Stored and READY"),
                @ApiResponse(responseCode = "400", ref = "BadRequest"),
                @ApiResponse(responseCode = "413", description = "File is larger than the limit"),
                @ApiResponse(responseCode = "415", description = "Not a CSV or PDF, or a contradicting type"),
                @ApiResponse(responseCode = "503", description = "Storage or access check unavailable")})
    @PostMapping(path = "/api/v1/projects/{projectId}/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReportResponse> upload(@PathVariable long projectId,
            @Parameter(description = "A .csv file in UTF-8 or a .pdf file") @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        try (InputStream content = file.getInputStream()) {
            var report = uploads.upload(new UploadReportCommand(projectId, CurrentUser.id(jwt),
                    file.getOriginalFilename(), file.getContentType(), file.getSize(), content));
            return ResponseEntity.created(URI.create("/api/v1/reports/" + report.id()))
                    .body(ReportResponse.of(report));
        }
    }

    @Operation(summary = "Queue the generation of a CSV or PDF report over a period",
            description = "Answers at once with the queued job; poll GET /api/v1/reports/{id} until READY, "
                    + "then download its content. A repeated request with the same Idempotency-Key and "
                    + "parameters returns the same job.",
            responses = {
                @ApiResponse(responseCode = "202", description = "Job stored as QUEUED"),
                @ApiResponse(responseCode = "400", ref = "BadRequest"),
                @ApiResponse(responseCode = "409", description = "Idempotency-Key reused with other parameters, "
                        + "or its report was deleted"),
                @ApiResponse(responseCode = "503", description = "Access check or data source unavailable")})
    @PostMapping(path = "/api/v1/projects/{projectId}/reports/generations",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReportResponse> generate(@PathVariable long projectId,
            @RequestBody GenerationRequest request,
            @Parameter(description = "Makes a retried request return the same job")
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        var report = generations.generate(new GenerateReportCommand(projectId, CurrentUser.id(jwt), request.from(),
                request.to(), request.format(), request.monitorIds(), idempotencyKey));
        return ResponseEntity.accepted().location(URI.create("/api/v1/reports/" + report.id()))
                .body(ReportResponse.of(report));
    }

    @Operation(summary = "List reports and generation jobs of a project, newest first",
            responses = @ApiResponse(responseCode = "200",
                    description = "Page of reports; totals in X-Total-Count and X-Has-Next"))
    @GetMapping("/api/v1/projects/{projectId}/reports")
    public ResponseEntity<List<ReportResponse>> list(@PathVariable long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportSource source,
            @AuthenticationPrincipal Jwt jwt) {
        var result = lists.list(new ListReportsQuery(projectId, CurrentUser.id(jwt), status, source, page, size));
        return ResponseEntity.ok()
                .header(TOTAL_COUNT_HEADER, Long.toString(result.total()))
                .header(HAS_NEXT_HEADER, Boolean.toString(result.hasNext()))
                .body(result.items().stream().map(ReportResponse::of).toList());
    }

    @Operation(summary = "Metadata and status of a report or generation job")
    @GetMapping("/api/v1/reports/{reportId}")
    public ReportResponse get(@PathVariable UUID reportId, @AuthenticationPrincipal Jwt jwt) {
        return ReportResponse.of(metadata.get(reportId, CurrentUser.id(jwt)));
    }

    /**
     * Streams the object straight to the client. Once the status line is sent it cannot change:
     * a failure after that closes the stream, and the client detects the short transfer by
     * Content-Length or by the SHA-256 in the metadata.
     */
    @Operation(summary = "Download the content of a READY report",
            responses = {
                @ApiResponse(responseCode = "200", description = "The file",
                        content = @Content(mediaType = "application/octet-stream",
                                schema = @Schema(type = "string", format = "binary"))),
                @ApiResponse(responseCode = "409", description = "The report is not READY"),
                @ApiResponse(responseCode = "503", description = "Content unavailable or missing")})
    @GetMapping("/api/v1/reports/{reportId}/content")
    public void content(@PathVariable UUID reportId, @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse response) throws IOException {
        var download = downloads.download(reportId, CurrentUser.id(jwt));
        log.info("Report download started: report_id={} user_id={}", reportId, CurrentUser.id(jwt));
        long transferred;
        try (var content = download.content()) {
            var report = download.report();
            response.setContentType(report.format().contentType());
            response.setContentLengthLong(report.sizeBytes());
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                    .filename(report.fileName().value(), StandardCharsets.UTF_8).build().toString());
            response.setHeader("X-Content-Type-Options", "nosniff");
            transferred = content.content().transferTo(response.getOutputStream());
        } catch (IOException interrupted) {
            log.warn("Report download did not complete: report_id={}", reportId, interrupted);
            throw interrupted;
        }
        log.info("Report download completed: report_id={} user_id={} bytes={}",
                reportId, CurrentUser.id(jwt), transferred);
    }

    @Operation(summary = "Delete a report or cancel its generation",
            responses = @ApiResponse(responseCode = "202", description = "Deletion accepted"))
    @DeleteMapping("/api/v1/reports/{reportId}")
    public ResponseEntity<Void> delete(@PathVariable UUID reportId, @AuthenticationPrincipal Jwt jwt) {
        deletions.delete(reportId, CurrentUser.id(jwt));
        return ResponseEntity.accepted().build();
    }
}
