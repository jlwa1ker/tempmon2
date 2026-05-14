package com.tempmon.model;

import java.util.List;

/**
 * Result returned by {@code ReadingValidator} after validating all readings in
 * a {@link Payload}.
 *
 * <p>Use the static factory methods to construct instances:
 * <ul>
 *   <li>{@link #valid(List)} — all readings passed validation.</li>
 *   <li>{@link #invalid(List)} — one or more readings failed validation.</li>
 * </ul>
 *
 * <p>When {@code valid} is {@code true}, {@code readings} contains the
 * converted {@link HygrometerReading} objects ready for duplicate filtering
 * and persistence. {@code failures} is empty.
 *
 * <p>When {@code valid} is {@code false}, {@code failures} contains one
 * {@link ValidationFailure} per failing reading (all failures are collected
 * before returning — no fail-fast). {@code readings} is empty.
 *
 * <p>Requirements: 3.2, 5.3
 */
public record ValidationResult(
        boolean valid,
        List<HygrometerReading> readings,
        List<ValidationFailure> failures
) {

    /**
     * Creates a successful validation result containing the converted readings.
     *
     * @param readings non-null, non-empty list of validated readings
     * @return a {@code ValidationResult} with {@code valid == true}
     */
    public static ValidationResult valid(List<HygrometerReading> readings) {
        return new ValidationResult(true, List.copyOf(readings), List.of());
    }

    /**
     * Creates a failed validation result containing all collected failures.
     *
     * @param failures non-null, non-empty list of validation failures
     * @return a {@code ValidationResult} with {@code valid == false}
     */
    public static ValidationResult invalid(List<ValidationFailure> failures) {
        return new ValidationResult(false, List.of(), List.copyOf(failures));
    }
}
