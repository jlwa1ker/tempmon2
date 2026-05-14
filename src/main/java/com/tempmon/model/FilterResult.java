package com.tempmon.model;

import java.util.List;

/**
 * Result returned by {@code DuplicateFilter} after partitioning a validated
 * reading list into records to persist and records to skip.
 *
 * <p>{@code readingsToInsert} contains every {@link HygrometerReading} that is
 * not a duplicate and should be written to DynamoDB.
 *
 * <p>{@code skipped} contains a {@link SkippedReading} descriptor for every
 * reading that was omitted, preserving the original payload index and the
 * skip reason.
 *
 * <p>Requirements: 10.3, 10.4
 */
public record FilterResult(
        List<HygrometerReading> readingsToInsert,
        List<SkippedReading> skipped
) {}
