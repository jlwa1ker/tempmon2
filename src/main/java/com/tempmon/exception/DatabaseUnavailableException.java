package com.tempmon.exception;

/**
 * Thrown when the database cannot be reached due to a connection or network error.
 * Maps to HTTP 503 (Service Unavailable).
 */
public class DatabaseUnavailableException extends RuntimeException {

    public DatabaseUnavailableException(String message) {
        super(message);
    }

    public DatabaseUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
