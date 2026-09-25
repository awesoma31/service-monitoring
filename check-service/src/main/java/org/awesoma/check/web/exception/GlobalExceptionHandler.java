package org.awesoma.check.web.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

/** The same RFC 7807 response shape as monitor-service, for the reactive stack. */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException exception, ServerWebExchange exchange) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage(), exchange);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ProblemDetail handleUnavailable(
            ServiceUnavailableException exception, ServerWebExchange exchange) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable", exception.getMessage(), exchange);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ProblemDetail handleBinding(WebExchangeBindException exception, ServerWebExchange exchange) {
        return validationProblem(exception.getAllErrors().stream()
                .map(error -> new ValidationViolation(
                        error instanceof FieldError field ? jsonName(field.getField()) : error.getObjectName(),
                        message(error)))
                .toList(), exchange);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleMethodValidation(
            HandlerMethodValidationException exception, ServerWebExchange exchange) {
        return validationProblem(exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ValidationViolation(
                                error instanceof FieldError field
                                        ? jsonName(field.getField())
                                        : result.getMethodParameter().getParameterName(),
                                message(error))))
                .toList(), exchange);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ProblemDetail handleInput(ServerWebInputException exception, ServerWebExchange exchange) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid request parameter",
                "A request value has an invalid format",
                exchange);
    }

    private ProblemDetail validationProblem(
            List<ValidationViolation> violations, ServerWebExchange exchange) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                "One or more request values are invalid",
                exchange);
        problem.setProperty("violations", violations);
        return problem;
    }

    private ProblemDetail problem(
            HttpStatus status, String title, String detail, ServerWebExchange exchange) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
        return problem;
    }

    /** Field errors carry Java names; report them the way the JSON names them. */
    private String jsonName(String field) {
        return objectMapper.getPropertyNamingStrategy() instanceof PropertyNamingStrategies.NamingBase naming
                ? naming.translate(field)
                : field;
    }

    private String message(MessageSourceResolvable error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : "is invalid";
    }

    public record ValidationViolation(String field, String message) {}
}
