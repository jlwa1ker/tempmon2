package com.tempmon.controller;

import com.tempmon.exception.DatabaseConstraintException;
import com.tempmon.exception.DatabaseUnavailableException;
import com.tempmon.exception.ParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler that maps exceptions to standardized JSON error responses.
 * <p>
 * Response format: {"status": "error", "error": "&lt;ERROR_CODE&gt;", "message": "&lt;human-readable message&gt;"}
 * <p>
 * Per Requirement 6.1: unhandled errors return 500 with no stack traces, class names, or file paths.
 * Per Requirement 6.2: full stack trace + X-Request-ID logged at ERROR level.
 * Per Requirement 6.6: if both timeout and unhandled error occur, return 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String MDC_REQUEST_ID_KEY = "requestId";

    /**
     * Handles request timeout (408).
     * Per Requirement 6.6, this only applies when no other unhandled error occurred.
     * The ordering of @ExceptionHandler methods ensures that more specific exceptions
     * (which would produce 500) are caught before this one.
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleTimeout(AsyncRequestTimeoutException ex) {
        log.warn("Request timed out [requestId={}]", MDC.get(MDC_REQUEST_ID_KEY));
        return buildErrorResponse(HttpStatus.REQUEST_TIMEOUT, "REQUEST_TIMEOUT", "Request timed out");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return buildErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Content-Type must be application/json");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "HTTP method " + ex.getMethod() + " is not supported for this endpoint");
    }

    @ExceptionHandler(ParseException.class)
    public ResponseEntity<Map<String, Object>> handleParseException(ParseException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getHttpStatus());
        return buildErrorResponse(status, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(DatabaseUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseUnavailable(DatabaseUnavailableException ex) {
        log.error("Database unavailable [requestId={}]: {}", MDC.get(MDC_REQUEST_ID_KEY), ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE",
                "Service temporarily unavailable");
    }

    @ExceptionHandler(DatabaseConstraintException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseConstraint(DatabaseConstraintException ex) {
        log.error("Database error [requestId={}]: {}", MDC.get(MDC_REQUEST_ID_KEY), ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_ERROR",
                "An internal error occurred");
    }

    /**
     * Catch-all handler for any unhandled exception.
     * Per Requirement 6.1: response body is sanitized (no stack trace, no class names, no file paths).
     * Per Requirement 6.2: full stack trace + X-Request-ID logged at ERROR level.
     * Per Requirement 6.6: returns 500 regardless of whether a timeout also occurred.
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Throwable ex) {
        String requestId = MDC.get(MDC_REQUEST_ID_KEY);
        log.error("Unhandled exception [requestId={}]", requestId, ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected internal error occurred");
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String errorCode, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("error", errorCode);
        body.put("message", message);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
