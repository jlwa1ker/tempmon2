package com.tempmon.model;

import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Wraps the raw parsed {@code readings} array produced by {@code PayloadParser}.
 *
 * <p>The {@code readings} field holds the Jackson {@link ArrayNode} extracted
 * from the top-level JSON object. Downstream components ({@code ReadingValidator})
 * iterate over this array to validate and convert each element into a
 * {@link HygrometerReading}.
 *
 * <p>Requirements: 2.1
 */
public record Payload(ArrayNode readings) {}
