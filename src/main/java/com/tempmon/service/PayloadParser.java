package com.tempmon.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.tempmon.exception.ParseException;
import com.tempmon.model.Payload;
import org.springframework.stereotype.Service;

/**
 * Parses a raw JSON request body into a {@link Payload}.
 *
 * <p>Validation steps (in order):
 * <ol>
 *   <li>Reject null/blank body → 400 {@code EMPTY_BODY}</li>
 *   <li>Parse JSON with Jackson; catch {@link JsonProcessingException} → 400 {@code MALFORMED_JSON}</li>
 *   <li>Assert top-level value is a JSON object → 400 {@code INVALID_PAYLOAD_STRUCTURE}</li>
 *   <li>Assert {@code readings} key exists and is an array → 400 {@code INVALID_PAYLOAD_STRUCTURE}</li>
 *   <li>Assert {@code readings} array is non-empty → 400 {@code EMPTY_READINGS}</li>
 * </ol>
 *
 * <p>Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7
 */
@Service
public class PayloadParser {

    private final ObjectMapper objectMapper;

    public PayloadParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses {@code rawBody} into a {@link Payload}.
     *
     * @param rawBody the raw HTTP request body string; may be {@code null} or blank
     * @return a {@link Payload} wrapping the parsed {@code readings} array
     * @throws ParseException if the body is empty, malformed, or structurally invalid
     */
    public Payload parse(String rawBody) {
        // Requirement 2.2 — reject empty body
        if (rawBody == null || rawBody.isBlank()) {
            throw new ParseException(400, "EMPTY_BODY", "Request body must not be empty");
        }

        // Requirement 2.3 — parse JSON; surface parse errors with detail
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (JsonProcessingException e) {
            throw new ParseException(400, "MALFORMED_JSON",
                    "Request body is not valid JSON: " + e.getOriginalMessage(), e);
        }

        // Requirement 2.4 — top-level value must be a JSON object
        if (!root.isObject()) {
            throw new ParseException(400, "INVALID_PAYLOAD_STRUCTURE",
                    "Request body must be a JSON object");
        }

        // Requirement 2.5 — 'readings' key must exist and be an array
        JsonNode readingsNode = root.get("readings");
        if (readingsNode == null || !readingsNode.isArray()) {
            throw new ParseException(400, "INVALID_PAYLOAD_STRUCTURE",
                    "'readings' must be a JSON array");
        }

        // Requirement 2.6 — 'readings' array must be non-empty
        ArrayNode readings = (ArrayNode) readingsNode;
        if (readings.isEmpty()) {
            throw new ParseException(400, "EMPTY_READINGS",
                    "'readings' array must not be empty");
        }

        // Requirement 2.7 — return Payload with the parsed readings array
        return new Payload(readings);
    }
}
