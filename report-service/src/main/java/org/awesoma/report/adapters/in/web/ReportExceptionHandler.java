package org.awesoma.report.adapters.in.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.report.application.content.ContentTooLargeException;
import org.awesoma.report.application.content.InvalidContentException;
import org.awesoma.report.application.content.UnsupportedContentTypeException;
import org.awesoma.report.application.port.AccessUnavailableException;
import org.awesoma.report.application.port.ObjectStorageUnavailableException;
import org.awesoma.report.application.usecase.IdempotencyConflictException;
import org.awesoma.report.application.usecase.ProjectAccessDeniedException;
import org.awesoma.report.application.usecase.ReportNotFoundException;
import org.awesoma.report.application.usecase.ReportNotReadyException;
import org.awesoma.report.application.usecase.ReportUnavailableException;
import org.awesoma.report.domain.InvalidReportRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * One RFC 7807 shape for every error, as in the other services. Details of the storage, the
 * database and the services behind an access check stay in the logs.
 */
@Slf4j
@RestControllerAdvice
public class ReportExceptionHandler {

    @ExceptionHandler(InvalidReportRequestException.class)
    public ProblemDetail invalidRequest(InvalidReportRequestException error, HttpServletRequest request) {
        var problem = problem(HttpStatus.BAD_REQUEST, "Request validation failed", error.getMessage(), request);
        problem.setProperty("violations", List.of(new Violation(error.field(), error.getMessage())));
        return problem;
    }

    @ExceptionHandler(InvalidContentException.class)
    public ProblemDetail invalidContent(InvalidContentException error, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid file content", error.getMessage(), request);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ProblemDetail malformed(Exception error, HttpServletRequest request) {
        String detail = error instanceof MissingServletRequestPartException missing
                ? "Part '%s' is required".formatted(missing.getRequestPartName())
                : error instanceof MethodArgumentTypeMismatchException mismatch
                ? "Parameter '%s' has an invalid value".formatted(mismatch.getName())
                : "Request is malformed";
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", detail, request);
    }

    @ExceptionHandler({ContentTooLargeException.class, MaxUploadSizeExceededException.class})
    public ProblemDetail tooLarge(Exception error, HttpServletRequest request) {
        String detail = error instanceof ContentTooLargeException tooLarge
                ? tooLarge.getMessage() : "File or request is larger than allowed";
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large", detail, request);
    }

    @ExceptionHandler(MultipartException.class)
    public ProblemDetail badMultipart(MultipartException error, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", "Multipart request cannot be read", request);
    }

    @ExceptionHandler({UnsupportedContentTypeException.class, HttpMediaTypeNotSupportedException.class})
    public ProblemDetail unsupported(Exception error, HttpServletRequest request) {
        String detail = error instanceof UnsupportedContentTypeException
                ? error.getMessage() : "Content type is not supported";
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", detail, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail unauthorized(AuthenticationException error, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "A valid Bearer token is required", request);
    }

    @ExceptionHandler({ProjectAccessDeniedException.class, AccessDeniedException.class})
    public ProblemDetail forbidden(Exception error, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "Insufficient permissions", request);
    }

    @ExceptionHandler(ReportNotFoundException.class)
    public ProblemDetail notFound(ReportNotFoundException error, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", error.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail idempotencyConflict(IdempotencyConflictException error, HttpServletRequest request) {
        var problem = problem(HttpStatus.CONFLICT, "Conflicting state", error.getMessage(), request);
        problem.setProperty("code", error.code());
        return problem;
    }

    @ExceptionHandler(ReportNotReadyException.class)
    public ProblemDetail notReady(ReportNotReadyException error, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Conflicting state", error.getMessage(), request);
    }

    @ExceptionHandler({ReportUnavailableException.class, AccessUnavailableException.class,
        ObjectStorageUnavailableException.class})
    public ProblemDetail unavailable(RuntimeException error, HttpServletRequest request) {
        log.warn("Request could not be completed: path={}", request.getRequestURI(), error);
        String detail = error instanceof AccessUnavailableException
                ? "Access permissions cannot currently be verified"
                : error instanceof ReportUnavailableException ? error.getMessage()
                : "Report storage is temporarily unavailable";
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable", detail, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    public record Violation(String field, String message) {
    }
}
