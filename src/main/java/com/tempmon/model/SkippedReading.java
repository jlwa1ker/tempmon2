package com.tempmon.model;

/**
 * Describes a reading that was skipped during duplicate filtering.
 *
 * <p>{@code index} is the zero-based position of the reading in the original
 * submitted payload.
 *
 * <p>{@code reason} is one of:
 * <ul>
 *   <li>{@code "exact_match"} — the reading already exists in DynamoDB with
 *       identical {@code temperature_f} and {@code humidity_pct} values.</li>
 *   <li>{@code "conflicting_data"} — the reading shares a
 *       {@code timestamp} + {@code location} key with either an earlier reading
 *       in the same batch (intra-batch duplicate) or an existing DynamoDB record
 *       whose field values differ (inter-batch conflicting duplicate).</li>
 * </ul>
 *
 * <p>Requirements: 10.3, 10.4
 */
public record SkippedReading(int index, String reason) {}
