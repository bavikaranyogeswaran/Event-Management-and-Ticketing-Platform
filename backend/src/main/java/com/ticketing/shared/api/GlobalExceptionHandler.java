package com.ticketing.shared.api;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ticketing.shared.web.RequestIdFilter;

import jakarta.validation.ConstraintViolationException;

/** Maps every exception to the standard error envelope with a stable code. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    /** Business errors thrown by application services. */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApi(ApiException ex) {
        return respond(ex.status(), ex.code(), ex.getMessage(), List.of());
    }

    /** Bean Validation failures on request DTOs. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiErrorResponse.FieldErrorEntry> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiErrorResponse.FieldErrorEntry(f.getField(), f.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Request validation failed.", fields);
    }

    /** Validation failures on single params (@RequestParam, @PathVariable). */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraint(ConstraintViolationException ex) {
        List<ApiErrorResponse.FieldErrorEntry> fields = ex.getConstraintViolations().stream()
                .map(v -> new ApiErrorResponse.FieldErrorEntry(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Request validation failed.", fields);
    }

    /** Two users edited the same row; client should reload and retry. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return respond(HttpStatus.CONFLICT, ErrorCodes.CONFLICT_RETRY,
                "The resource was modified by another request. Reload and try again.", List.of());
    }

    /** Method-security denials (@PreAuthorize). */
    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleDenied(AuthorizationDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, ErrorCodes.FORBIDDEN, "You do not have permission for this action.",
                List.of());
    }

    /** Anything unexpected: log the details, return a generic message — never leak internals. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR,
                "An unexpected error occurred.", List.of());
    }

    private ResponseEntity<ApiErrorResponse> respond(HttpStatus status, String code, String message,
            List<ApiErrorResponse.FieldErrorEntry> fields) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(clock), status.value(), code, message, fields, MDC.get(RequestIdFilter.MDC_KEY));
        return ResponseEntity.status(status).body(body);
    }
}
