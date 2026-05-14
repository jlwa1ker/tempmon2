package com.tempmon.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Immutable in-memory representation of a single hygrometer reading,
 * produced by {@code ReadingValidator} after successful validation.
 *
 * <p>This record is the canonical domain object passed between
 * {@code ReadingValidator}, {@code DuplicateFilter}, and {@code StorageService}.
 * It is never persisted directly — {@link ReadingItem} is the DynamoDB mapping class.
 *
 * <p>Requirements: 2.1, 3.1
 */
public record HygrometerReading(
        OffsetDateTime timestamp,
        BigDecimal temperatureF,
        BigDecimal humidityPct,
        String location
) {}
