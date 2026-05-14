package com.tempmon.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 * Validates Requirements 6.1 and 6.2.
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
}
