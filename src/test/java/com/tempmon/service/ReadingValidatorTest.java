package com.tempmon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tempmon.model.Payload;
import com.tempmon.model.ValidationFailure;
import com.tempmon.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ReadingValidator}.
 *
 * Validates: Requirements 3.1, 3.2, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10
 */
class ReadingValidatorTest {

    private ReadingValidator validator;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        validator = new ReadingValidator();
        mapper = new ObjectMapper();
    }

    // --- Helper methods ---

    private ObjectNode validReading() {
        ObjectNode node = mapper.createObjectNode();
        node.put("timestamp", "2024-01-15T10:30:00Z");
        node.put("temperature_f", 72.5);
        node.put("humidity_pct", 45.0);
        node.put("location", "Kitchen");
        return node;
    }

    private Payload payloadOf(ObjectNode... readings) {
        ArrayNode array = mapper.createArrayNode();
        for (ObjectNode r : readings) {
            array.add(r);
        }
        return new Payload(array);
    }

    private Payload payloadWithRawNode(com.fasterxml.jackson.databind.JsonNode... nodes) {
        ArrayNode array = mapper.createArrayNode();
        for (com.fasterxml.jackson.databind.JsonNode n : nodes) {
            array.add(n);
        }
        return new Payload(array);
    }

    // --- Tests ---

    @Nested
    @DisplayName("Requirement 3.1: All-valid batch")
    class AllValidBatch {

        @Test
        @DisplayName("Single valid reading returns valid result with one HygrometerReading")
        void singleValidReading() {
            Payload payload = payloadOf(validReading());

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isTrue();
            assertThat(result.readings()).hasSize(1);
            assertThat(result.failures()).isEmpty();
        }

        @Test
        @DisplayName("Multiple valid readings returns valid result with all readings")
        void multipleValidReadings() {
            ObjectNode r1 = validReading();
            ObjectNode r2 = validReading();
            r2.put("timestamp", "2024-02-20T08:00:00+05:30");
            r2.put("location", "Garage");
            Payload payload = payloadOf(r1, r2);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isTrue();
            assertThat(result.readings()).hasSize(2);
            assertThat(result.failures()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Requirement 3.5: timestamp validation")
    class TimestampValidation {

        @Test
        @DisplayName("Missing timestamp field records failure")
        void missingTimestamp() {
            ObjectNode reading = validReading();
            reading.remove("timestamp");
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).index()).isEqualTo(0);
            assertThat(result.failures().get(0).field()).isEqualTo("timestamp");
        }

        @Test
        @DisplayName("Non-RFC3339 timestamp records failure")
        void invalidTimestampFormat() {
            ObjectNode reading = validReading();
            reading.put("timestamp", "not-a-date");
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).field()).isEqualTo("timestamp");
        }

        @Test
        @DisplayName("Null timestamp records failure")
        void nullTimestamp() {
            ObjectNode reading = validReading();
            reading.putNull("timestamp");
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).field()).isEqualTo("timestamp");
        }

        @Test
        @DisplayName("Non-string timestamp records failure")
        void numericTimestamp() {
            ObjectNode reading = validReading();
            reading.put("timestamp", 12345);
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).field()).isEqualTo("timestamp");
        }
    }

    @Nested
    @DisplayName("Requirements 3.6, 3.7: temperature_f validation")
    class TemperatureValidation {

        @Test
        @DisplayName("Missing temperature_f records failure")
        void missingTemperature() {
            ObjectNode reading = validReading();
            reading.remove("temperature_f");
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).index()).isEqualTo(0);
            assertThat(result.failures().get(0).field()).isEqualTo("temperature_f");
        }

        @Test
        @DisplayName("Non-numeric temperature_f records failure")
        void nonNumericTemperature() {
            ObjectNode reading = validReading();
            reading.put("temperature_f", "hot");
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).field()).isEqualTo("temperature_f");
        }

        @Test
        @DisplayName("temperature_f at -100 (lower boundary) passes")
        void temperatureAtLowerBoundary() {
            ObjectNode reading = validReading();
            reading.put("temperature_f", -100);
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("temperature_f at 200 (upper boundary) passes")
        void temperatureAtUpperBoundary() {
            ObjectNode reading = validReading();
            reading.put("temperature_f", 200);
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("temperature_f at -100.1 (below lower boundary) fails")
        void temperatureBelowLowerBoundary() {
            ObjectNode reading = validReading();
            reading.put("temperature_f", -100.1);
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).field()).isEqualTo("temperature_f");
        }

        @Test
        @DisplayName("temperature_f at 200.1 (above upper boundary) fails")
        void temperatureAboveUpperBoundary() {
            ObjectNode reading = validReading();
            reading.put("temperature_f", 200.1);
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).field()).isEqualTo("temperature_f");
        }
    }

    @Nested
    @DisplayName("Requirements 3.8, 3.9: humidity_pct validation")
    class HumidityValidation {

        @Test
        @DisplayName("Missing humidity_pct records failure")
        void missingHumidity() {
            ObjectNode reading = validReading();
            reading.remove("humidity_pct");
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).index()).isEqualTo(0);
            assertThat(result.failures().get(0).field()).isEqualTo("humidity_pct");
        }

        @Test
        @DisplayName("Non-numeric humidity_pct records failure")
        void nonNumericHumidity() {
            ObjectNode reading = validReading();
            reading.put("humidity_pct", "wet");
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).field()).isEqualTo("humidity_pct");
        }

        @Test
        @DisplayName("humidity_pct at 0 (lower boundary) passes")
        void humidityAtLowerBoundary() {
            ObjectNode reading = validReading();
            reading.put("humidity_pct", 0);
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("humidity_pct at 100 (upper boundary) passes")
        void humidityAtUpperBoundary() {
            ObjectNode reading = validReading();
            reading.put("humidity_pct", 100);
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("humidity_pct at -0.1 (below lower boundary) fails")
        void humidityBelowLowerBoundary() {
            ObjectNode reading = validReading();
            reading.put("humidity_pct", -0.1);
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).field()).isEqualTo("humidity_pct");
        }

        @Test
        @DisplayName("humidity_pct at 100.1 (above upper boundary) fails")
        void humidityAboveUpperBoundary() {
            ObjectNode reading = validReading();
            reading.put("humidity_pct", 100.1);
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).field()).isEqualTo("humidity_pct");
        }
    }

    @Nested
    @DisplayName("Requirement 3.10: location validation")
    class LocationValidation {

        @Test
        @DisplayName("Missing location records failure")
        void missingLocation() {
            ObjectNode reading = validReading();
            reading.remove("location");
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).index()).isEqualTo(0);
            assertThat(result.failures().get(0).field()).isEqualTo("location");
        }

        @Test
        @DisplayName("location with 1 character passes")
        void locationOneChar() {
            ObjectNode reading = validReading();
            reading.put("location", "A");
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("location with 255 characters passes")
        void location255Chars() {
            ObjectNode reading = validReading();
            reading.put("location", "A".repeat(255));
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isTrue();
        }

        @Test
        @DisplayName("location with 256 characters fails")
        void location256Chars() {
            ObjectNode reading = validReading();
            reading.put("location", "A".repeat(256));
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).field()).isEqualTo("location");
        }

        @Test
        @DisplayName("empty location fails")
        void emptyLocation() {
            ObjectNode reading = validReading();
            reading.put("location", "");
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).field()).isEqualTo("location");
        }

        @Test
        @DisplayName("whitespace-only location fails")
        void whitespaceOnlyLocation() {
            ObjectNode reading = validReading();
            reading.put("location", "   \t\n  ");
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).field()).isEqualTo("location");
        }

        @Test
        @DisplayName("null location records failure")
        void nullLocation() {
            ObjectNode reading = validReading();
            reading.putNull("location");
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).field()).isEqualTo("location");
        }
    }

    @Nested
    @DisplayName("Requirement 3.2: Multiple failures collected")
    class MultipleFailures {

        @Test
        @DisplayName("Multiple failures in one reading are all reported")
        void multipleFailuresInOneReading() {
            ObjectNode reading = mapper.createObjectNode();
            // Missing all required fields
            Payload payload = payloadOf(reading);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            // Should have failures for timestamp, temperature_f, humidity_pct, location
            assertThat(result.failures()).hasSize(4);
            assertThat(result.failures())
                    .extracting(ValidationFailure::field)
                    .containsExactlyInAnyOrder("timestamp", "temperature_f", "humidity_pct", "location");
            // All failures should reference index 0
            assertThat(result.failures())
                    .extracting(ValidationFailure::index)
                    .containsOnly(0);
        }

        @Test
        @DisplayName("Failures across multiple readings are all reported with correct indices")
        void failuresAcrossMultipleReadings() {
            ObjectNode r0 = validReading();
            r0.remove("timestamp"); // failure at index 0

            ObjectNode r1 = validReading(); // valid at index 1

            ObjectNode r2 = validReading();
            r2.put("humidity_pct", 150); // failure at index 2

            Payload payload = payloadOf(r0, r1, r2);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(2);
            assertThat(result.failures().get(0).index()).isEqualTo(0);
            assertThat(result.failures().get(0).field()).isEqualTo("timestamp");
            assertThat(result.failures().get(1).index()).isEqualTo(2);
            assertThat(result.failures().get(1).field()).isEqualTo("humidity_pct");
        }
    }

    @Nested
    @DisplayName("Requirement 3.4: Non-object reading item")
    class NonObjectReadingItem {

        @Test
        @DisplayName("Array item that is a number records failure")
        void numberItem() {
            ArrayNode array = mapper.createArrayNode();
            array.add(42);
            Payload payload = new Payload(array);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).index()).isEqualTo(0);
            assertThat(result.failures().get(0).field()).isEqualTo("reading");
        }

        @Test
        @DisplayName("Array item that is a string records failure")
        void stringItem() {
            ArrayNode array = mapper.createArrayNode();
            array.add("not an object");
            Payload payload = new Payload(array);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).index()).isEqualTo(0);
            assertThat(result.failures().get(0).field()).isEqualTo("reading");
        }

        @Test
        @DisplayName("Array item that is null records failure")
        void nullItem() {
            ArrayNode array = mapper.createArrayNode();
            array.addNull();
            Payload payload = new Payload(array);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).index()).isEqualTo(0);
            assertThat(result.failures().get(0).field()).isEqualTo("reading");
        }

        @Test
        @DisplayName("Array item that is a nested array records failure")
        void nestedArrayItem() {
            ArrayNode array = mapper.createArrayNode();
            array.add(mapper.createArrayNode());
            Payload payload = new Payload(array);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).index()).isEqualTo(0);
            assertThat(result.failures().get(0).field()).isEqualTo("reading");
        }

        @Test
        @DisplayName("Mix of non-object and valid items reports correct indices")
        void mixedItems() {
            ArrayNode array = mapper.createArrayNode();
            array.add(42);                // index 0 - non-object
            array.add(validReading());    // index 1 - valid
            array.add("bad");            // index 2 - non-object
            Payload payload = new Payload(array);

            ValidationResult result = validator.validate(payload);

            assertThat(result.valid()).isFalse();
            assertThat(result.failures()).hasSize(2);
            assertThat(result.failures().get(0).index()).isEqualTo(0);
            assertThat(result.failures().get(1).index()).isEqualTo(2);
        }
    }
}
