package com.tempmon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tempmon.model.Payload;
import com.tempmon.model.ValidationFailure;
import com.tempmon.model.ValidationResult;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 3: Out-of-range numeric field rejection
 *
 * Generator: arbitrary readings with temperature_f outside [-100, 200] OR humidity_pct outside [0, 100]
 * Assertion: result is invalid; failure list contains an entry for the out-of-range field
 * with the received value and valid range
 *
 * Validates: Requirements 3.7, 3.9
 */
@Tag("Feature_json-http-ingestion")
@Tag("Property_3_out-of-range-numeric-field-rejection")
class ReadingValidatorOutOfRangePropertyTest {

    private final ReadingValidator validator = new ReadingValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Property: Any reading with temperature_f outside [-100, 200] is rejected,
     * and the failure message mentions the field name, received value, and valid range.
     *
     * Validates: Requirements 3.7
     */
    @Property(tries = 200)
    void temperatureOutOfRangeIsRejected(@ForAll("outOfRangeTemperatures") BigDecimal temperature) {
        ObjectNode reading = validReading();
        reading.put("temperature_f", temperature);

        Payload payload = toPayload(reading);
        ValidationResult result = validator.validate(payload);

        assertThat(result.valid()).isFalse();

        List<ValidationFailure> tempFailures = result.failures().stream()
                .filter(f -> f.field().equals("temperature_f"))
                .toList();

        assertThat(tempFailures).isNotEmpty();

        ValidationFailure failure = tempFailures.get(0);
        assertThat(failure.index()).isEqualTo(0);
        assertThat(failure.message()).contains(temperature.stripTrailingZeros().toPlainString());
        assertThat(failure.message()).contains("[-100, 200]");
    }

    /**
     * Property: Any reading with humidity_pct outside [0, 100] is rejected,
     * and the failure message mentions the field name, received value, and valid range.
     *
     * Validates: Requirements 3.9
     */
    @Property(tries = 200)
    void humidityOutOfRangeIsRejected(@ForAll("outOfRangeHumidities") BigDecimal humidity) {
        ObjectNode reading = validReading();
        reading.put("humidity_pct", humidity);

        Payload payload = toPayload(reading);
        ValidationResult result = validator.validate(payload);

        assertThat(result.valid()).isFalse();

        List<ValidationFailure> humidityFailures = result.failures().stream()
                .filter(f -> f.field().equals("humidity_pct"))
                .toList();

        assertThat(humidityFailures).isNotEmpty();

        ValidationFailure failure = humidityFailures.get(0);
        assertThat(failure.index()).isEqualTo(0);
        assertThat(failure.message()).contains(humidity.stripTrailingZeros().toPlainString());
        assertThat(failure.message()).contains("[0, 100]");
    }

    /**
     * Property: When both temperature_f and humidity_pct are out of range,
     * both failures are reported.
     *
     * Validates: Requirements 3.7, 3.9
     */
    @Property(tries = 100)
    void bothFieldsOutOfRangeProducesBothFailures(
            @ForAll("outOfRangeTemperatures") BigDecimal temperature,
            @ForAll("outOfRangeHumidities") BigDecimal humidity) {

        ObjectNode reading = validReading();
        reading.put("temperature_f", temperature);
        reading.put("humidity_pct", humidity);

        Payload payload = toPayload(reading);
        ValidationResult result = validator.validate(payload);

        assertThat(result.valid()).isFalse();

        List<String> failedFields = result.failures().stream()
                .map(ValidationFailure::field)
                .toList();

        assertThat(failedFields).contains("temperature_f", "humidity_pct");
    }

    // --- Generators ---

    @Provide
    Arbitrary<BigDecimal> outOfRangeTemperatures() {
        // temperature_f valid range is [-100, 200]; generate values outside this range
        return Arbitraries.oneOf(
                // Below -100: values from -100.01 down to -10000
                Arbitraries.bigDecimals()
                        .between(new BigDecimal("-10000"), new BigDecimal("-100.01"))
                        .ofScale(2),
                // Above 200: values from 200.01 up to 10000
                Arbitraries.bigDecimals()
                        .between(new BigDecimal("200.01"), new BigDecimal("10000"))
                        .ofScale(2)
        );
    }

    @Provide
    Arbitrary<BigDecimal> outOfRangeHumidities() {
        // humidity_pct valid range is [0, 100]; generate values outside this range
        return Arbitraries.oneOf(
                // Below 0: values from -0.01 down to -1000
                Arbitraries.bigDecimals()
                        .between(new BigDecimal("-1000"), new BigDecimal("-0.01"))
                        .ofScale(2),
                // Above 100: values from 100.01 up to 1000
                Arbitraries.bigDecimals()
                        .between(new BigDecimal("100.01"), new BigDecimal("1000"))
                        .ofScale(2)
        );
    }

    // --- Helpers ---

    private ObjectNode validReading() {
        ObjectNode node = mapper.createObjectNode();
        node.put("timestamp", "2024-06-15T12:00:00Z");
        node.put("temperature_f", 72.0);
        node.put("humidity_pct", 45.0);
        node.put("location", "TestRoom");
        return node;
    }

    private Payload toPayload(ObjectNode... readings) {
        ArrayNode array = mapper.createArrayNode();
        for (ObjectNode reading : readings) {
            array.add(reading);
        }
        return new Payload(array);
    }
}
