package org.awesoma.monitoring.web.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns failures at the HTTP boundary into one RFC 7807 response shape. Database details and
 * parser internals deliberately stay out of the response: they are useful in logs, but expose
 * implementation details without helping an API client fix its request.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(
            NotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Resource not found",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(ConflictStateException.class)
    public ProblemDetail handleConflict(
            ConflictStateException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Conflicting state",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(BindException.class)
    public ProblemDetail handleBindingValidation(
            BindException exception, HttpServletRequest request) {
        List<ValidationViolation> violations = exception.getBindingResult().getAllErrors().stream()
                .map(error -> new ValidationViolation(
                        error instanceof FieldError fieldError
                                ? jsonName(fieldError.getField())
                                : error.getObjectName(),
                        message(error)))
                .toList();
        return validationProblem(violations, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleMethodValidation(
            HandlerMethodValidationException exception, HttpServletRequest request) {
        List<ValidationViolation> violations = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ValidationViolation(fieldName(result, error), message(error))))
                .toList();
        return validationProblem(violations, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Invalid request parameter",
                "Parameter '%s' has an invalid value".formatted(exception.getName()),
                request);
        problem.setProperty(
                "violations",
                List.of(new ValidationViolation(exception.getName(), "has an invalid value")));
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableMessage(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Malformed request body",
                "Request body is malformed or contains an unsupported value",
                request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Conflicting data",
                "The request conflicts with existing data",
                request);
    }

    private ProblemDetail validationProblem(
            List<ValidationViolation> violations, HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                "One or more request values are invalid",
                request);
        problem.setProperty("violations", violations);
        return problem;
    }

    private ProblemDetail problem(
            HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        return problemDetail;
    }

    /**
     * A body validated alongside a constrained path variable arrives here rather than as a
     * BindException, with its errors wrapped per parameter. The client needs the name of the
     * offending field, not the name of the controller's method argument.
     */
    private String fieldName(ParameterValidationResult result, MessageSourceResolvable error) {
        return error instanceof FieldError fieldError
                ? jsonName(fieldError.getField())
                : parameterName(result);
    }

    /**
     * Field errors carry Java property names. The client wrote the JSON names, which follow
     * the configured naming strategy, so the violation must name the field the same way.
     */
    private String jsonName(String field) {
        return objectMapper.getPropertyNamingStrategy() instanceof PropertyNamingStrategies.NamingBase naming
                ? naming.translate(field)
                : field;
    }

    private String parameterName(ParameterValidationResult result) {
        String name = result.getMethodParameter().getParameterName();
        return name != null
                ? name
                : "argument[%d]".formatted(result.getMethodParameter().getParameterIndex());
    }

    private String message(MessageSourceResolvable error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : "is invalid";
    }

    public record ValidationViolation(String field, String message) {}
}
