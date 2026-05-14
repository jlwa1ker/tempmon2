package com.tempmon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tempmon.exception.ParseException;
import com.tempmon.model.Payload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PayloadParser}.
 *
 * Validates: Requirements 2.2, 2.3, 2.4, 2.5, 2.6, 2.7
 */
class PayloadParserTest {

    private PayloadParser parser;

    @BeforeEach
    void setUp() {
        parser = new PayloadParser(new ObjectMapper());
    }

    // --- Requirement 2.3: Empty body → 400 EMPTY_BODY ---

    @Nested
    @DisplayName("Empty body → 400 EMPTY_BODY")
    class EmptyBody {

        @Test
        @DisplayName("null body throws EMPTY_BODY")
        void nullBody() {
            ParseException ex = assertThrows(ParseException.class, () -> parser.parse(null));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("EMPTY_BODY", ex.getErrorCode());
        }

        @Test
        @DisplayName("empty string body throws EMPTY_BODY")
        void emptyString() {
            ParseException ex = assertThrows(ParseException.class, () -> parser.parse(""));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("EMPTY_BODY", ex.getErrorCode());
        }

        @Test
        @DisplayName("whitespace-only body throws EMPTY_BODY")
        void whitespaceOnly() {
            ParseException ex = assertThrows(ParseException.class, () -> parser.parse("   \t\n  "));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("EMPTY_BODY", ex.getErrorCode());
        }
    }

    // --- Requirement 2.2: Malformed JSON → 400 MALFORMED_JSON ---

    @Nested
    @DisplayName("Malformed JSON → 400 MALFORMED_JSON")
    class MalformedJson {

        @Test
        @DisplayName("unterminated string")
        void unterminatedString() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("{\"readings\": [\"unterminated"));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("MALFORMED_JSON", ex.getErrorCode());
            assertNotNull(ex.getMessage());
            assertFalse(ex.getMessage().isBlank());
        }

        @Test
        @DisplayName("trailing comma")
        void trailingComma() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("{\"readings\": [1,]}"));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("MALFORMED_JSON", ex.getErrorCode());
        }

        @Test
        @DisplayName("random text")
        void randomText() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("not json at all"));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("MALFORMED_JSON", ex.getErrorCode());
        }

        @Test
        @DisplayName("unclosed brace")
        void unclosedBrace() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("{\"readings\": ["));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("MALFORMED_JSON", ex.getErrorCode());
        }

        @Test
        @DisplayName("message contains descriptive detail")
        void descriptiveMessage() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("{invalid}"));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("MALFORMED_JSON", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("not valid JSON"),
                    "Expected message to contain 'not valid JSON', got: " + ex.getMessage());
        }
    }

    // --- Requirement 2.4: Top-level array/string/null → 400 INVALID_PAYLOAD_STRUCTURE ---

    @Nested
    @DisplayName("Top-level non-object → 400 INVALID_PAYLOAD_STRUCTURE")
    class TopLevelNotObject {

        @Test
        @DisplayName("top-level array")
        void topLevelArray() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("[1, 2, 3]"));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("INVALID_PAYLOAD_STRUCTURE", ex.getErrorCode());
        }

        @Test
        @DisplayName("top-level string")
        void topLevelString() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("\"hello\""));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("INVALID_PAYLOAD_STRUCTURE", ex.getErrorCode());
        }

        @Test
        @DisplayName("top-level null")
        void topLevelNull() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("null"));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("INVALID_PAYLOAD_STRUCTURE", ex.getErrorCode());
        }

        @Test
        @DisplayName("top-level number")
        void topLevelNumber() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("42"));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("INVALID_PAYLOAD_STRUCTURE", ex.getErrorCode());
        }

        @Test
        @DisplayName("top-level boolean")
        void topLevelBoolean() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("true"));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("INVALID_PAYLOAD_STRUCTURE", ex.getErrorCode());
        }
    }

    // --- Requirement 2.5: Missing 'readings' key → 400 INVALID_PAYLOAD_STRUCTURE ---

    @Nested
    @DisplayName("Missing 'readings' key → 400 INVALID_PAYLOAD_STRUCTURE")
    class MissingReadingsKey {

        @Test
        @DisplayName("object with no readings key")
        void noReadingsKey() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("{\"data\": [1, 2, 3]}"));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("INVALID_PAYLOAD_STRUCTURE", ex.getErrorCode());
        }

        @Test
        @DisplayName("empty object")
        void emptyObject() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("{}"));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("INVALID_PAYLOAD_STRUCTURE", ex.getErrorCode());
        }
    }

    // --- Requirement 2.5: 'readings' not an array → 400 INVALID_PAYLOAD_STRUCTURE ---

    @Nested
    @DisplayName("'readings' not an array → 400 INVALID_PAYLOAD_STRUCTURE")
    class ReadingsNotArray {

        @Test
        @DisplayName("readings is a string")
        void readingsIsString() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("{\"readings\": \"not an array\"}"));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("INVALID_PAYLOAD_STRUCTURE", ex.getErrorCode());
        }

        @Test
        @DisplayName("readings is a number")
        void readingsIsNumber() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("{\"readings\": 42}"));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("INVALID_PAYLOAD_STRUCTURE", ex.getErrorCode());
        }

        @Test
        @DisplayName("readings is an object")
        void readingsIsObject() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("{\"readings\": {\"a\": 1}}"));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("INVALID_PAYLOAD_STRUCTURE", ex.getErrorCode());
        }

        @Test
        @DisplayName("readings is null")
        void readingsIsNull() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("{\"readings\": null}"));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("INVALID_PAYLOAD_STRUCTURE", ex.getErrorCode());
        }

        @Test
        @DisplayName("readings is a boolean")
        void readingsIsBoolean() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("{\"readings\": true}"));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("INVALID_PAYLOAD_STRUCTURE", ex.getErrorCode());
        }
    }

    // --- Requirement 2.6: Empty 'readings' array → 400 EMPTY_READINGS ---

    @Nested
    @DisplayName("Empty 'readings' array → 400 EMPTY_READINGS")
    class EmptyReadingsArray {

        @Test
        @DisplayName("empty readings array")
        void emptyReadingsArray() {
            ParseException ex = assertThrows(ParseException.class,
                    () -> parser.parse("{\"readings\": []}"));
            assertEquals(400, ex.getHttpStatus());
            assertEquals("EMPTY_READINGS", ex.getErrorCode());
        }
    }

    // --- Requirement 2.7: Valid payload → Payload with correct structure ---

    @Nested
    @DisplayName("Valid payload → Payload with correct structure")
    class ValidPayload {

        @Test
        @DisplayName("single reading returns Payload with one element")
        void singleReading() {
            String body = """
                    {
                      "readings": [
                        {
                          "timestamp": "2024-01-15T10:30:00Z",
                          "temperature_f": 72.5,
                          "humidity_pct": 45.2,
                          "location": "Kitchen"
                        }
                      ]
                    }
                    """;

            Payload payload = parser.parse(body);

            assertNotNull(payload);
            assertNotNull(payload.readings());
            assertEquals(1, payload.readings().size());
            assertEquals("Kitchen", payload.readings().get(0).get("location").asText());
            assertEquals(72.5, payload.readings().get(0).get("temperature_f").asDouble(), 0.001);
            assertEquals(45.2, payload.readings().get(0).get("humidity_pct").asDouble(), 0.001);
            assertEquals("2024-01-15T10:30:00Z", payload.readings().get(0).get("timestamp").asText());
        }

        @Test
        @DisplayName("multiple readings returns Payload with correct count")
        void multipleReadings() {
            String body = """
                    {
                      "readings": [
                        {"timestamp": "2024-01-15T10:30:00Z", "temperature_f": 72.5, "humidity_pct": 45.2, "location": "Kitchen"},
                        {"timestamp": "2024-01-15T11:00:00Z", "temperature_f": 68.0, "humidity_pct": 50.0, "location": "Garage"}
                      ]
                    }
                    """;

            Payload payload = parser.parse(body);

            assertNotNull(payload);
            assertEquals(2, payload.readings().size());
        }

        @Test
        @DisplayName("extra keys in top-level object are ignored")
        void extraTopLevelKeys() {
            String body = """
                    {
                      "metadata": "ignored",
                      "readings": [
                        {"timestamp": "2024-01-15T10:30:00Z", "temperature_f": 72.5, "humidity_pct": 45.2, "location": "Kitchen"}
                      ]
                    }
                    """;

            Payload payload = parser.parse(body);

            assertNotNull(payload);
            assertEquals(1, payload.readings().size());
        }

        @Test
        @DisplayName("readings with extra fields are preserved")
        void extraFieldsInReadings() {
            String body = """
                    {
                      "readings": [
                        {"timestamp": "2024-01-15T10:30:00Z", "temperature_f": 72.5, "humidity_pct": 45.2, "location": "Kitchen", "extra": "value"}
                      ]
                    }
                    """;

            Payload payload = parser.parse(body);

            assertNotNull(payload);
            assertEquals(1, payload.readings().size());
            assertEquals("value", payload.readings().get(0).get("extra").asText());
        }
    }
}
