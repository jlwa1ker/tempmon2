package com.tempmon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tempmon.model.Payload;
import com.tempmon.model.ValidationFailure;
import com.tempmon.model.ValidationResult;
import net.jqwik.api.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 8: Validation failure completeness
 *
 * <p>For any payload where multiple readings fail validation, the failure list
 * SHALL contain a failure entry for every failing reading (not just the first),
 * and each entry SHALL include the zero-based index of the failing item.
 *
 * <p><b>Validates: Requirements 3.2</b>
 */
@Tag("Feature: json-http-ingestion")
@Tag("Property 8: validation failure completeness")
class ValidationFailureCompletenessPropertyTest {

    private final ReadingValidator validator = new ReadingValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Property: For any payload containing a mix of valid and invalid readings
     * (with at least one invalid), the validation result contains at least one
     * failure entry for every invalid reading index, and no failure entry
     * references a valid reading index.
     */
    @Property(tries = 200)
    void failureListContainsExactlyOneEntryPerFailingReading(
            @ForAll("payloadsWithInvalidSubset") PayloadWithExpectedFailures input) {

        ValidationResult result = validator.validate(input.payload());

        // The result must be invalid since we guarantee at least one invalid reading
        assertThat(result.valid()).isFalse();

        List<ValidationFailure> failures = result.failures();

        // Collect the set of indices that have failures
        Set<Integer> failedIndices = failures.stream()
                .map(ValidationFailure::index)
                .collect(Collectors.toSet());

        // Every expected-invalid index must appear in the failure list
        for (int expectedIdx : input.invalidIndices()) {
            assertThat(failedIndices)
                    .as("Expected a failure for reading at index %d", expectedIdx)
                    .contains(expectedIdx);
        }

        // No failure should reference a valid reading index
        for (ValidationFailure failure : failures) {
            assertThat(input.invalidIndices())
                    .as("Failure at index %d should only reference an invalid reading",
                            failure.index())
                    .contains(failure.index());
        }

        // Every invalid index must have at least one failure entry
        // (completeness: no failing reading is omitted)
        assertThat(failedIndices)
                .as("All invalid reading indices must be represented in failures")
                .containsAll(input.invalidIndices());
    }

    @Provide
    Arbitrary<PayloadWithExpectedFailures> payloadsWithInvalidSubset() {
        // Generate a list size between 2 and 20
        return Arbitraries.integers().between(2, 20).flatMap(size ->
            // For each position, decide if it's valid or invalid
            Arbitraries.of(true, false).list().ofSize(size).flatMap(validFlags -> {
                // Ensure at least one is invalid
                boolean hasInvalid = validFlags.stream().anyMatch(v -> !v);
                List<Boolean> finalFlags;
                if (!hasInvalid) {
                    finalFlags = new java.util.ArrayList<>(validFlags);
                    finalFlags.set(0, false);
                } else {
                    finalFlags = validFlags;
                }

                Set<Integer> invalidIndices = new HashSet<>();
                for (int i = 0; i < finalFlags.size(); i++) {
                    if (!finalFlags.get(i)) {
                        invalidIndices.add(i);
                    }
                }

                return buildPayloadArbitrary(finalFlags, invalidIndices);
            })
        );
    }

    private Arbitrary<PayloadWithExpectedFailures> buildPayloadArbitrary(
            List<Boolean> validFlags, Set<Integer> invalidIndices) {

        List<Arbitrary<ObjectNode>> readingArbitraries = new java.util.ArrayList<>();
        for (boolean isValid : validFlags) {
            if (isValid) {
                readingArbitraries.add(validReadingArbitrary());
            } else {
                readingArbitraries.add(invalidReadingArbitrary());
            }
        }

        return Combinators.combine(readingArbitraries).as(readings -> {
            ArrayNode arrayNode = mapper.createArrayNode();
            for (ObjectNode reading : readings) {
                arrayNode.add(reading);
            }
            return new PayloadWithExpectedFailures(new Payload(arrayNode), invalidIndices);
        });
    }

    private Arbitrary<ObjectNode> validReadingArbitrary() {
        Arbitrary<String> timestamps = Arbitraries.of(
                "2024-01-15T10:30:00Z",
                "2023-06-01T00:00:00+05:30",
                "2025-12-31T23:59:59-08:00",
                "2024-07-04T12:00:00Z"
        );
        Arbitrary<Double> temperatures = Arbitraries.doubles().between(-100.0, 200.0);
        Arbitrary<Double> humidities = Arbitraries.doubles().between(0.0, 100.0);
        Arbitrary<String> locations = Arbitraries.strings()
                .alpha().ofMinLength(1).ofMaxLength(50);

        return Combinators.combine(timestamps, temperatures, humidities, locations)
                .as((ts, temp, hum, loc) -> {
                    ObjectNode node = mapper.createObjectNode();
                    node.put("timestamp", ts);
                    node.put("temperature_f", temp);
                    node.put("humidity_pct", hum);
                    node.put("location", loc);
                    return node;
                });
    }

    private Arbitrary<ObjectNode> invalidReadingArbitrary() {
        return Arbitraries.integers().between(0, 5).flatMap(strategy -> {
            switch (strategy) {
                case 0: return missingTimestampReading();
                case 1: return outOfRangeTemperatureReading();
                case 2: return outOfRangeHumidityReading();
                case 3: return invalidLocationReading();
                case 4: return invalidTimestampFormatReading();
                case 5: return multipleInvalidFieldsReading();
                default: return missingTimestampReading();
            }
        });
    }

    private Arbitrary<ObjectNode> missingTimestampReading() {
        Arbitrary<Double> temperatures = Arbitraries.doubles().between(-100.0, 200.0);
        Arbitrary<Double> humidities = Arbitraries.doubles().between(0.0, 100.0);
        Arbitrary<String> locations = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);

        return Combinators.combine(temperatures, humidities, locations)
                .as((temp, hum, loc) -> {
                    ObjectNode node = mapper.createObjectNode();
                    node.put("temperature_f", temp);
                    node.put("humidity_pct", hum);
                    node.put("location", loc);
                    return node;
                });
    }

    private Arbitrary<ObjectNode> outOfRangeTemperatureReading() {
        Arbitrary<Double> badTemps = Arbitraries.oneOf(
                Arbitraries.doubles().between(-1000.0, -100.01),
                Arbitraries.doubles().between(200.01, 1000.0)
        );
        Arbitrary<Double> humidities = Arbitraries.doubles().between(0.0, 100.0);
        Arbitrary<String> locations = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);

        return Combinators.combine(badTemps, humidities, locations)
                .as((temp, hum, loc) -> {
                    ObjectNode node = mapper.createObjectNode();
                    node.put("timestamp", "2024-01-15T10:30:00Z");
                    node.put("temperature_f", temp);
                    node.put("humidity_pct", hum);
                    node.put("location", loc);
                    return node;
                });
    }

    private Arbitrary<ObjectNode> outOfRangeHumidityReading() {
        Arbitrary<Double> temperatures = Arbitraries.doubles().between(-100.0, 200.0);
        Arbitrary<Double> badHumidities = Arbitraries.oneOf(
                Arbitraries.doubles().between(-500.0, -0.01),
                Arbitraries.doubles().between(100.01, 500.0)
        );
        Arbitrary<String> locations = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);

        return Combinators.combine(temperatures, badHumidities, locations)
                .as((temp, hum, loc) -> {
                    ObjectNode node = mapper.createObjectNode();
                    node.put("timestamp", "2024-01-15T10:30:00Z");
                    node.put("temperature_f", temp);
                    node.put("humidity_pct", hum);
                    node.put("location", loc);
                    return node;
                });
    }

    private Arbitrary<ObjectNode> invalidLocationReading() {
        Arbitrary<String> badLocations = Arbitraries.oneOf(
                Arbitraries.just(""),
                Arbitraries.of("   ", "\t", "\n", "  \t\n  "),
                Arbitraries.strings().alpha().ofMinLength(256).ofMaxLength(300)
        );
        Arbitrary<Double> temperatures = Arbitraries.doubles().between(-100.0, 200.0);
        Arbitrary<Double> humidities = Arbitraries.doubles().between(0.0, 100.0);

        return Combinators.combine(temperatures, humidities, badLocations)
                .as((temp, hum, loc) -> {
                    ObjectNode node = mapper.createObjectNode();
                    node.put("timestamp", "2024-01-15T10:30:00Z");
                    node.put("temperature_f", temp);
                    node.put("humidity_pct", hum);
                    node.put("location", loc);
                    return node;
                });
    }

    private Arbitrary<ObjectNode> invalidTimestampFormatReading() {
        Arbitrary<String> badTimestamps = Arbitraries.of(
                "not-a-date",
                "2024-13-01T00:00:00Z",
                "2024/01/15 10:30:00",
                "Jan 15, 2024",
                "12345"
        );
        Arbitrary<Double> temperatures = Arbitraries.doubles().between(-100.0, 200.0);
        Arbitrary<Double> humidities = Arbitraries.doubles().between(0.0, 100.0);
        Arbitrary<String> locations = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);

        return Combinators.combine(badTimestamps, temperatures, humidities, locations)
                .as((ts, temp, hum, loc) -> {
                    ObjectNode node = mapper.createObjectNode();
                    node.put("timestamp", ts);
                    node.put("temperature_f", temp);
                    node.put("humidity_pct", hum);
                    node.put("location", loc);
                    return node;
                });
    }

    private Arbitrary<ObjectNode> multipleInvalidFieldsReading() {
        Arbitrary<Double> badTemps = Arbitraries.doubles().between(200.01, 1000.0);
        Arbitrary<Double> badHumidities = Arbitraries.doubles().between(100.01, 500.0);

        return Combinators.combine(badTemps, badHumidities)
                .as((temp, hum) -> {
                    ObjectNode node = mapper.createObjectNode();
                    node.put("timestamp", "not-a-valid-timestamp");
                    node.put("temperature_f", temp);
                    node.put("humidity_pct", hum);
                    node.put("location", "");
                    return node;
                });
    }

    record PayloadWithExpectedFailures(Payload payload, Set<Integer> invalidIndices) {}
}
