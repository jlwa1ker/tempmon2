package com.tempmon.exception;

/**
 * Thrown when the request body cannot be parsed into a valid payload.
 * Carries the HTTP status code to return and a machine-readable error code
 * (e.g. {@code "EMPTY_BODY"}, {@code "MALFORMED_JSON"}, {@code "INVALID_PAYLOAD_STRUCTURE"},
 * {@code "EMPTY_READINGS"}).
 *
 * <p>Maps to HTTP 400 (Bad Request) in all current usages, but the status is carried
 * explicitly so the {@code GlobalExceptionHandler} can forward it without hard-coding it.</p>
 */
public class ParseException extends RuntimeException {

    private final int httpStatus;
    private final String errorCode;

    public ParseException(int httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public ParseException(int httpStatus, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    /** The HTTP status code that should be returned to the client (e.g. 400). */
    public int getHttpStatus() {
        return httpStatus;
    }

    /** A machine-readable error code string (e.g. {@code "MALFORMED_JSON"}). */
    public String getErrorCode() {
        return errorCode;
    }
}
