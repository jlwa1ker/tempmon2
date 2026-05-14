package com.tempmon.model;

/**
 * Describes a single field-level validation failure for one reading in a payload.
 *
 * <p>{@code index} is the zero-based position of the failing reading in the
 * submitted {@code readings} array.
 *
 * <p>{@code field} is the name of the field that failed validation
 * (e.g. {@code "timestamp"}, {@code "temperature_f"}, {@code "humidity_pct"},
 * {@code "location"}).
 *
 * <p>{@code message} is a human-readable description of the failure, including
 * the received value and the valid range or format where applicable.
 *
 * <p>Requirements: 3.2, 5.3
 */
public record ValidationFailure(int index, String field, String message) {}
