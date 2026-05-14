package com.tempmon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.tempmon.model.HygrometerReading;
import com.tempmon.model.Payload;
import com.tempmon.model.ValidationFailure;
import com.tempmon.model.ValidationResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates each reading in a {@link Payload} and returns a {@link ValidationResult}.
 *
 * <p>All failures are collected before returning — no fail-fast. If every reading
 * passes, the result contains the converted {@link HygrometerReading} list. If any
 * reading fails, the result contains the full list of {@link ValidationFailure} records.
 *
 * <p>Validation rules per reading (Requirements 3.4–3.10):
 * <ul>
 *   <li>Item must be a JSON object (3.4)</li>
 *   <li>{@code timestamp}: present, string, RFC 3339 parseable (3.5)</li>
 *   <li>{@code temperature_f}: present, numeric, in [-100, 200] (3.6, 3.7)</li>
 *   <li>{@code humidity_pct}: present, numeric, in [0, 100] (3.8, 3.9)</li>
 *   <li>{@code location}: present, non-empty string, 1–255 chars, not whitespace-only (3.10)</li>
 * </ul>
 *
 * <p>Requirements: 3.1, 3.2, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10
 */
@Service
public class ReadingValidator {

    private static final BigDecimal TEMP_MIN = new BigDecimal("-100");
    private static final BigDecimal TEMP_MAX = new BigDecimal("200");
    private static final BigDecimal HUMIDITY_MIN = BigDecimal.ZERO;
    private static final BigDecimal HUMIDITY_MAX = new BigDecimal("100");

    /**
     * Validates all readings in the given {@link Payload}.
     *
     * @param payload the parsed payload containing the raw readings array
     * @return {@link ValidationResult#valid(List)} if all readings pass,
     *         {@link ValidationResult#invalid(List)} if any reading fails
     */
    public ValidationResult validate(Payload payload) {
        ArrayNode readings = payload.readings();
        List<ValidationFailure> failures = new ArrayList<>();
        List<HygrometerReading> valid = new ArrayList<>();

        for (int i = 0; i < readings.size(); i++) {
            JsonNode item = readings.get(i);
            List<ValidationFailure> itemFailures = validateItem(i, item);
            if (itemFailures.isEmpty()) {
                valid.add(toReading(item));
            } else {
                failures.addAll(itemFailures);
            }
        }

        if (failures.isEmpty()) {
            return ValidationResult.valid(valid);
        }
        return ValidationResult.invalid(failures);
    }

    /**
     * Validates a single reading item at the given index.
     *
     * @param index zero-based position in the readings array
     * @param item  the JSON node to validate
     * @return list of failures (empty if the item is valid)
     */
    private List<ValidationFailure> validateItem(int index, JsonNode item) {
        List<ValidationFailure> failures = new ArrayList<>();

        // Requirement 3.4: item must be a JSON object
        if (!item.isObject()) {
            failures.add(new ValidationFailure(index, "reading",
                    "each reading must be a JSON object"));
            // Cannot validate fields if the item is not an object
            return failures;
        }

        // Requirement 3.5: timestamp — present, string, RFC 3339 parseable
        validateTimestamp(index, item, failures);

        // Requirements 3.6, 3.7: temperature_f — present, numeric, in [-100, 200]
        validateTemperature(index, item, failures);

        // Requirements 3.8, 3.9: humidity_pct — present, numeric, in [0, 100]
        validateHumidity(index, item, failures);

        // Requirement 3.10: location — present, non-empty string, 1–255 chars, not whitespace-only
        validateLocation(index, item, failures);

        return failures;
    }

    private void validateTimestamp(int index, JsonNode item, List<ValidationFailure> failures) {
        JsonNode node = item.get("timestamp");
        if (node == null || node.isNull()) {
            failures.add(new ValidationFailure(index, "timestamp",
                    "timestamp is required and must be an RFC 3339 string " +
                    "(e.g. 2024-01-15T10:30:00Z)"));
            return;
        }
        if (!node.isTextual()) {
            failures.add(new ValidationFailure(index, "timestamp",
                    "timestamp must be a string conforming to RFC 3339 " +
                    "(e.g. 2024-01-15T10:30:00Z); received a non-string value"));
            return;
        }
        String raw = node.textValue();
        try {
            OffsetDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            failures.add(new ValidationFailure(index, "timestamp",
                    "timestamp must be a string conforming to RFC 3339 " +
                    "(e.g. 2024-01-15T10:30:00Z); received: \"" + raw + "\""));
        }
    }

    private void validateTemperature(int index, JsonNode item, List<ValidationFailure> failures) {
        JsonNode node = item.get("temperature_f");
        if (node == null || node.isNull()) {
            failures.add(new ValidationFailure(index, "temperature_f",
                    "temperature_f is required and must be a number"));
            return;
        }
        if (!node.isNumber()) {
            failures.add(new ValidationFailure(index, "temperature_f",
                    "temperature_f must be a number; received a non-numeric value"));
            return;
        }
        BigDecimal value = node.decimalValue();
        if (value.compareTo(TEMP_MIN) < 0 || value.compareTo(TEMP_MAX) > 0) {
            failures.add(new ValidationFailure(index, "temperature_f",
                    "temperature_f value " + value + " is outside valid range [-100, 200]"));
        }
    }

    private void validateHumidity(int index, JsonNode item, List<ValidationFailure> failures) {
        JsonNode node = item.get("humidity_pct");
        if (node == null || node.isNull()) {
            failures.add(new ValidationFailure(index, "humidity_pct",
                    "humidity_pct is required and must be a number"));
            return;
        }
        if (!node.isNumber()) {
            failures.add(new ValidationFailure(index, "humidity_pct",
                    "humidity_pct must be a number; received a non-numeric value"));
            return;
        }
        BigDecimal value = node.decimalValue();
        if (value.compareTo(HUMIDITY_MIN) < 0 || value.compareTo(HUMIDITY_MAX) > 0) {
            failures.add(new ValidationFailure(index, "humidity_pct",
                    "humidity_pct value " + value + " is outside valid range [0, 100]"));
        }
    }

    private void validateLocation(int index, JsonNode item, List<ValidationFailure> failures) {
        JsonNode node = item.get("location");
        if (node == null || node.isNull()) {
            failures.add(new ValidationFailure(index, "location",
                    "location is required and must be a non-empty string of at most 255 characters"));
            return;
        }
        if (!node.isTextual()) {
            failures.add(new ValidationFailure(index, "location",
                    "location must be a string; received a non-string value"));
            return;
        }
        String value = node.textValue();
        if (value.isEmpty() || value.isBlank() || value.length() > 255) {
            failures.add(new ValidationFailure(index, "location",
                    "location must be a non-empty string of at most 255 characters " +
                    "(whitespace-only strings are invalid); received: \"" + value + "\""));
        }
    }

    /**
     * Converts a validated JSON object node into a {@link HygrometerReading}.
     * Only call this after {@link #validateItem} returns no failures.
     */
    private HygrometerReading toReading(JsonNode item) {
        OffsetDateTime timestamp = OffsetDateTime.parse(item.get("timestamp").textValue());
        BigDecimal temperatureF = item.get("temperature_f").decimalValue();
        BigDecimal humidityPct = item.get("humidity_pct").decimalValue();
        String location = item.get("location").textValue();
        return new HygrometerReading(timestamp, temperatureF, humidityPct, location);
    }
}
