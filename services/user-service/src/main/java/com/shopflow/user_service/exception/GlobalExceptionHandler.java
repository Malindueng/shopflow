package com.shopflow.user_service.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralised exception handling.
 * Returns RFC 7807 ProblemDetail for every error so clients get a
 * consistent JSON shape regardless of which exception was thrown.
 *
 * Response shape:
 * {
 *   "type":     "https://shopflow.io/errors/not-found",
 *   "title":    "Resource Not Found",
 *   "status":   404,
 *   "detail":   "User not found with id: ...",
 *   "instance": "/api/v1/users/...",
 *   "timestamp": "2024-01-01T00:00:00Z"
 * }
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String ERROR_BASE_URI = "https://shopflow.io/errors/";

    // ── 404 Not Found ─────────────────────────────────────────────────

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUserNotFound(
            UserNotFoundException ex, WebRequest request) {
        return buildProblem(HttpStatus.NOT_FOUND, "not-found", "Resource Not Found",
                ex.getMessage(), request);
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTenantNotFound(
            TenantNotFoundException ex, WebRequest request) {
        return buildProblem(HttpStatus.NOT_FOUND, "not-found", "Resource Not Found",
                ex.getMessage(), request);
    }

    // ── 409 Conflict ──────────────────────────────────────────────────

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateEmail(
            DuplicateEmailException ex, WebRequest request) {
        return buildProblem(HttpStatus.CONFLICT, "duplicate-resource", "Duplicate Resource",
                ex.getMessage(), request);
    }

    // ── 401 Unauthorized ──────────────────────────────────────────────

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidToken(
            InvalidTokenException ex, WebRequest request) {
        return buildProblem(HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized",
                ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(
            BadCredentialsException ex, WebRequest request) {
        // Don't expose whether it's wrong email or wrong password
        return buildProblem(HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized",
                "Invalid email or password", request);
    }

    // ── 403 Forbidden ─────────────────────────────────────────────────

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            AuthorizationDeniedException ex, WebRequest request) {
        return buildProblem(HttpStatus.FORBIDDEN, "forbidden", "Access Denied",
                "You do not have permission to perform this action", request);
    }

    // ── 400 Validation errors ─────────────────────────────────────────

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field   = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            fieldErrors.put(field, message);
        });

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setType(URI.create(ERROR_BASE_URI + "validation-error"));
        problem.setTitle("Validation Error");
        problem.setProperty("errors", fieldErrors);
        problem.setProperty("timestamp", Instant.now().toString());

        return ResponseEntity.badRequest().body(problem);
    }

    // ── 500 Unexpected ────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAll(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        return buildProblem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.", request);
    }

    // ── Helper ────────────────────────────────────────────────────────

    private ResponseEntity<ProblemDetail> buildProblem(
            HttpStatus status, String errorCode, String title,
            String detail, WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(ERROR_BASE_URI + errorCode));
        problem.setTitle(title);
        problem.setProperty("timestamp", Instant.now().toString());

        String path = request.getDescription(false).replace("uri=", "");
        problem.setInstance(URI.create(path));

        return ResponseEntity.status(status).body(problem);
    }
}