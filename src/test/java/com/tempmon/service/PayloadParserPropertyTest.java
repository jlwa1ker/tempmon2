package com.tempmon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tempmon.model.Payload;
import net.jqwik.api.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for PayloadParser round-trip fidelity.
 *
 * <p>Feature: json-http-ingestion
 * <p>Property 1: parsing round-trip fidelity
 *
 * <p><b>Validates: Requirements 2.8</b>
 */
@Tag("json-http-ingestion")
@Tag("parsing-round-trip-fidelity")
class PayloadParserPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PayloadParser parser = new PayloadParser(objectMapper);

    /**
     * Property 1: Parsing round-trip fidelity
     *
     * For any valid JSON payload body, parsing the body into a Payload, serializing it
     * back to JSON, and re-parsing it SHALL produce a result with identical keys, values,
     * and data types at every level of nesting as the original parsed Payload.
     */
    @Property(tries = 200)
    void parsingRoundTripPreservesAllKeysValuesAndTypes(
            @ForAll("validPayloadJson") String jsonBody) {

        // First parse
        Payload firstParse = parser.parse(jsonBody);

        // Serialize back to JSON: wrap the readings ArrayNode in a top-level object
        String serialized = serializePayload(firstParse);

        // Second parse
        Payload secondParse = parser.parse(serialized);

        // Assert: second parse result equals first parse result
        assertThat(secondParse.readings())
                .as("Re-parsed readings should equal original parsed readings")
                .isEqualTo(firstParse.readings());
    }

    /**
     * Serializes a Payload back to a JSON string with the same structure as the input.
     */
    private String serializePayload(Payload payload) {
        var root = objectMapper.createObjectNode();
        root.set("readings", payload.readings());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }

    /**
     * Generator: arbitrary valid JSON objects with a readings array of 1–50 items,
     * each with valid field values (timestamp, temperature_f, humidity_pct, location).
     */
    @Provide
    Arbitrary<String> validPayloadJson() {
        return readingCount().flatMap(count -> {
            Arbitrary<String> readingArb = validReading();
            return readingArb.list().ofSize(count).map(readings -> {
                StringBuilder sb = new StringBuilder();
                sb.append("{\"readings\":[");
                for (int i = 0; i < readings.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(readings.get(i));
                }
                sb.append("]}");
                return sb.toString();
            });
        });
    }

    private Arbitrary<Integer> readingCount() {
        return Arbitraries.integers().between(1, 50);
    }

    /**
     * Generates a single valid reading JSON object with:
     * - timestamp: valid RFC 3339 datetime
     * - temperature_f: number in [-100, 200]
     * - humidity_pct: number in [0, 100]
     * - location: non-empty string of 1-255 characters
     */
    private Arbitrary<String> validReading() {
        Arbitrary<String> timestamp = validTimestamp();
        Arbitrary<BigDecimal> temperature = Arbitraries.bigDecimals()
                .between(new BigDecimal("-100"), new BigDecimal("200"))
                .ofScale(2);
        Arbitrary<BigDecimal> humidity = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("100"))
                .ofScale(2);
        Arbitrary<String> location = validLocation();

        return Combinators.combine(timestamp, temperature, humidity, location)
                .as((ts, temp, hum, loc) -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("{");
                    sb.append("\"timestamp\":\"").append(ts).append("\",");
                    sb.append("\"temperature_f\":").append(temp.toPlainString()).append(",");
                    sb.append("\"humidity_pct\":").append(hum.toPlainString()).append(",");
                    sb.append("\"location\":\"").append(escapeJson(loc)).append("\"");
                    sb.append("}");
                    return sb.toString();
                });
    }

    /**
     * Generates valid RFC 3339 timestamps.
     */
    private Arbitrary<String> validTimestamp() {
        return Combinators.combine(
                Arbitraries.integers().between(2000, 2030),  // year
                Arbitraries.integers().between(1, 12),       // month
                Arbitraries.integers().between(1, 28),       // day (safe for all months)
                Arbitraries.integers().between(0, 23),       // hour
                Arbitraries.integers().between(0, 59),       // minute
                Arbitraries.integers().between(0, 59)        // second
        ).as((year, month, day, hour, minute, second) -> {
            OffsetDateTime dt = OffsetDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.UTC);
            return dt.toString();
        });
    }

    /**
     * Generates valid location strings: non-empty, 1-255 alphanumeric characters.
     */
    private Arbitrary<String> validLocation() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    /**
     * Escapes special JSON characters in a string value.
     */
    private String escapeJson(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
