package com.tempmon.exception;

/**
 * Thrown when a DynamoDB transaction is cancelled or another SDK constraint violation occurs.
 * Maps to HTTP 500 (Internal Server Error).
 */
public class DatabaseConstraintException extends RuntimeException {

    public DatabaseConstraintException(String message) {
        super(message);
    }

    public DatabaseConstraintException(String message, Throwable cause) {
        super(message, cause);
    }
}
