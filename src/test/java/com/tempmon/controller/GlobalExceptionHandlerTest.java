package com.tempmon.controller;

import com.tempmon.exception.DatabaseConstraintException;
import com.tempmon.exception.DatabaseUnavailableException;
import com.tempmon.exception.ParseException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 * Validates Requirements 6.1, 6.2, and 6.3.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // --- AsyncRequestTimeoutException → 408 with REQUEST_TIMEOUT ---

    @Test
    void handleTimeout_returns408WithRequestTimeoutErrorCode() {
        AsyncRequestTimeoutException ex = new AsyncRequestTimeoutException();

        ResponseEntity<Map<String, Object>> response = handler.handleTimeout(ex);

        assertEquals(HttpStatus.REQUEST_TIMEOUT, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("error", body.get("status"));
        assertEquals("REQUEST_TIMEOUT", body.get("error"));
        assertNotNull(body.get("message"));
    }

    // --- HttpMediaTypeNotSupportedException → 415 ---

    @Test
    void handleUnsupportedMediaType_returns415WithUnsupportedMediaTypeErrorCode() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException("text/plain is not supported");

        ResponseEntity<Map<String, Object>> response = handler.handleUnsupportedMediaType(ex);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("error", body.get("status"));
        assertEquals("UNSUPPORTED_MEDIA_TYPE", body.get("error"));
        assertNotNull(body.get("message"));
    }

    // --- Arbitrary RuntimeException → 500 with no stack trace in body ---

    @Test
    void handleUnexpected_returns500WithInternalErrorCode() {
        RuntimeException ex = new RuntimeException("Something went wrong internally");

        ResponseEntity<Map<String, Object>> response = handler.handleUnexpected(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("error", body.get("status"));
        assertEquals("INTERNAL_ERROR", body.get("error"));
        assertNotNull(body.get("message"));
    }

    @Test
    void handleUnexpected_doesNotExposeExceptionMessageInBody() {
        String sensitiveMessage = "NullPointerException at com.tempmon.service.StorageService.persist(StorageService.java:42)";
        RuntimeException ex = new RuntimeException(sensitiveMessage);

        ResponseEntity<Map<String, Object>> response = handler.handleUnexpected(ex);

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        String message = (String) body.get("message");
        assertFalse(message.contains("NullPointerException"), "Response should not contain exception class name");
        assertFalse(message.contains("StorageService"), "Response should not contain internal class names");
        assertFalse(message.contains(".java"), "Response should not contain file paths");
        assertFalse(message.contains("com.tempmon"), "Response should not contain package names");
    }

    @Test
    void handleUnexpected_doesNotExposeStackTraceInBody() {
        RuntimeException ex = new RuntimeException("fail");
        ex.fillInStackTrace();

        ResponseEntity<Map<String, Object>> response = handler.handleUnexpected(ex);

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        // Verify no body value contains stack trace patterns
        for (Object value : body.values()) {
            if (value instanceof String strValue) {
                assertFalse(strValue.contains("at com."), "Response body should not contain stack trace lines");
                assertFalse(strValue.contains(".java:"), "Response body should not contain file references");
            }
        }
    }

    @Test
    void handleUnexpected_responseBodyConformsToErrorSchema() {
        RuntimeException ex = new RuntimeException("unexpected error");

        ResponseEntity<Map<String, Object>> response = handler.handleUnexpected(ex);

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        // Must contain exactly the three required keys
        assertTrue(body.containsKey("status"), "Body must contain 'status' key");
        assertTrue(body.containsKey("error"), "Body must contain 'error' key");
        assertTrue(body.containsKey("message"), "Body must contain 'message' key");
        assertEquals("error", body.get("status"));
        assertInstanceOf(String.class, body.get("error"));
        assertInstanceOf(String.class, body.get("message"));
    }

    @Test
    void handleUnexpected_withNestedCause_doesNotExposeInternals() {
        Exception cause = new IllegalStateException("DB connection pool exhausted");
        RuntimeException ex = new RuntimeException("Service failure", cause);

        ResponseEntity<Map<String, Object>> response = handler.handleUnexpected(ex);

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        String message = (String) body.get("message");
        assertFalse(message.contains("IllegalStateException"));
        assertFalse(message.contains("DB connection pool"));
        assertFalse(message.contains("Service failure"));
    }

    // --- HttpRequestMethodNotSupportedException → 405 ---

    @Test
    void handleMethodNotAllowed_returns405WithMethodNotAllowedErrorCode() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("DELETE");

        ResponseEntity<Map<String, Object>> response = handler.handleMethodNotAllowed(ex);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("error", body.get("status"));
        assertEquals("METHOD_NOT_ALLOWED", body.get("error"));
        assertNotNull(body.get("message"));
        assertTrue(((String) body.get("message")).contains("DELETE"),
                "Message should mention the unsupported method");
    }

    @Test
    void handleMethodNotAllowed_messageIncludesMethodName() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PUT");

        ResponseEntity<Map<String, Object>> response = handler.handleMethodNotAllowed(ex);

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(((String) body.get("message")).contains("PUT"));
    }

    // --- ParseException → correct status and error code ---

    @Test
    void handleParseException_returns400WithEmptyBodyErrorCode() {
        ParseException ex = new ParseException(400, "EMPTY_BODY", "A non-empty JSON body is required");

        ResponseEntity<Map<String, Object>> response = handler.handleParseException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("error", body.get("status"));
        assertEquals("EMPTY_BODY", body.get("error"));
        assertEquals("A non-empty JSON body is required", body.get("message"));
    }

    @Test
    void handleParseException_returns400WithMalformedJsonErrorCode() {
        ParseException ex = new ParseException(400, "MALFORMED_JSON", "Unexpected token at position 5");

        ResponseEntity<Map<String, Object>> response = handler.handleParseException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("error", body.get("status"));
        assertEquals("MALFORMED_JSON", body.get("error"));
        assertEquals("Unexpected token at position 5", body.get("message"));
    }

    @Test
    void handleParseException_returns400WithInvalidPayloadStructureErrorCode() {
        ParseException ex = new ParseException(400, "INVALID_PAYLOAD_STRUCTURE", "A top-level JSON object is required");

        ResponseEntity<Map<String, Object>> response = handler.handleParseException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("error", body.get("status"));
        assertEquals("INVALID_PAYLOAD_STRUCTURE", body.get("error"));
        assertEquals("A top-level JSON object is required", body.get("message"));
    }

    @Test
    void handleParseException_returns400WithEmptyReadingsErrorCode() {
        ParseException ex = new ParseException(400, "EMPTY_READINGS", "The readings array must contain at least one item");

        ResponseEntity<Map<String, Object>> response = handler.handleParseException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("error", body.get("status"));
        assertEquals("EMPTY_READINGS", body.get("error"));
        assertEquals("The readings array must contain at least one item", body.get("message"));
    }

    @Test
    void handleParseException_responseConformsToErrorSchema() {
        ParseException ex = new ParseException(400, "MALFORMED_JSON", "parse error");

        ResponseEntity<Map<String, Object>> response = handler.handleParseException(ex);

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("status"));
        assertTrue(body.containsKey("error"));
        assertTrue(body.containsKey("message"));
        assertEquals("error", body.get("status"));
        assertInstanceOf(String.class, body.get("error"));
        assertInstanceOf(String.class, body.get("message"));
    }

    // --- DatabaseUnavailableException → 503 ---

    @Test
    void handleDatabaseUnavailable_returns503WithDatabaseUnavailableErrorCode() {
        DatabaseUnavailableException ex = new DatabaseUnavailableException("Connection refused to localhost:8000");

        ResponseEntity<Map<String, Object>> response = handler.handleDatabaseUnavailable(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("error", body.get("status"));
        assertEquals("DATABASE_UNAVAILABLE", body.get("error"));
        assertNotNull(body.get("message"));
    }

    @Test
    void handleDatabaseUnavailable_doesNotExposeConnectionDetailsInBody() {
        DatabaseUnavailableException ex = new DatabaseUnavailableException(
                "Failed to connect to dynamodb at localhost:8000");

        ResponseEntity<Map<String, Object>> response = handler.handleDatabaseUnavailable(ex);

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        String message = (String) body.get("message");
        assertFalse(message.contains("localhost"), "Response should not expose host details");
        assertFalse(message.contains("8000"), "Response should not expose port details");
    }

    // --- DatabaseConstraintException → 500 ---

    @Test
    void handleDatabaseConstraint_returns500WithDatabaseErrorCode() {
        DatabaseConstraintException ex = new DatabaseConstraintException(
                "Transaction cancelled: ConditionalCheckFailed");

        ResponseEntity<Map<String, Object>> response = handler.handleDatabaseConstraint(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("error", body.get("status"));
        assertEquals("DATABASE_ERROR", body.get("error"));
        assertNotNull(body.get("message"));
    }

    @Test
    void handleDatabaseConstraint_doesNotExposeInternalDetailsInBody() {
        DatabaseConstraintException ex = new DatabaseConstraintException(
                "TransactionCanceledException: ConditionalCheckFailed for key {location=Kitchen, timestamp=2024-01-15T10:30:00Z}");

        ResponseEntity<Map<String, Object>> response = handler.handleDatabaseConstraint(ex);

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        String message = (String) body.get("message");
        assertFalse(message.contains("TransactionCanceledException"),
                "Response should not expose exception class names");
        assertFalse(message.contains("ConditionalCheckFailed"),
                "Response should not expose DynamoDB error details");
        assertFalse(message.contains("Kitchen"),
                "Response should not expose data values");
    }
}
