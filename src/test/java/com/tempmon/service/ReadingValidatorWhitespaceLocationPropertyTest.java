package com.tempmon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tempmon.model.Payload;
import com.tempmon.model.ValidationFailure;
import com.tempmon.model.ValidationResult;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 2: Whitespace-only and empty location rejection.
 *
 * <p>For any HygrometerReading where the location field is a string composed
 * entirely of whitespace characters (or is empty), the ReadingValidator SHALL
 * reject it and include a validation failure for that item.
 *
 * <p><b>Validates: Requirements 3.10</b>
 */
@Tag("Feature: json-http-ingestion")
@Tag("Property 2: whitespace-only and empty location rejection")
class ReadingValidatorWhitespaceLocationPropertyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ReadingValidator validator = new ReadingValidator();

    /**
     * Generator: arbitrary HygrometerReading with location drawn from strings
     * of only whitespace characters (space, tab, newline) of length 0–255.
     *
     * Assertion: result is invalid; failure list contains exactly one entry for location.
     */
    @Property(tries = 200)
    void whitespaceOnlyOrEmptyLocationIsAlwaysRejected(
            @ForAll("whitespaceLocations") String location) {

        // Build a valid reading except for the location field
        ObjectNode reading = MAPPER.createObjectNode();
        reading.put("timestamp", "2024-06-15T12:00:00Z");
        reading.put("temperature_f", 72.5);
        reading.put("humidity_pct", 45.0);
        reading.put("location", location);

        ArrayNode readings = MAPPER.createArrayNode();
        readings.add(reading);

        Payload payload = new Payload(readings);
        ValidationResult result = validator.validate(payload);

        // The result must be invalid
        assertThat(result.valid()).isFalse();

        // The failure list must contain exactly one entry for "location"
        List<ValidationFailure> failures = result.failures();
        List<ValidationFailure> locationFailures = failures.stream()
                .filter(f -> "location".equals(f.field()))
                .toList();

        assertThat(locationFailures)
                .as("Expected exactly one validation failure for 'location' field when location is whitespace-only or empty: \"%s\" (length=%d)",
                        location.replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r"),
                        location.length())
                .hasSize(1);

        // The failure should reference index 0 (the only reading)
        assertThat(locationFailures.get(0).index()).isEqualTo(0);
    }

    @Provide
    Arbitrary<String> whitespaceLocations() {
        // Whitespace characters: space, tab, newline, carriage return
        Arbitrary<Character> whitespaceChar = Arbitraries.of(' ', '\t', '\n', '\r');

        return Arbitraries.integers().between(0, 255).flatMap(length -> {
            if (length == 0) {
                return Arbitraries.just("");
            }
            return whitespaceChar.list().ofSize(length)
                    .map(chars -> {
                        StringBuilder sb = new StringBuilder(length);
                        for (Character c : chars) {
                            sb.append(c);
                        }
                        return sb.toString();
                    });
        });
    }
}
