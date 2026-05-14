package com.tempmon.model;

import java.util.List;

/**
 * Result returned by {@code StorageService} after successfully persisting a
 * batch of readings to DynamoDB.
 *
 * <p>{@code requestIds} is an ordered list of UUID v4 strings, one per
 * inserted reading, in the same order as the input list passed to
 * {@code StorageService.persist()}. Each UUID uniquely identifies the
 * corresponding DynamoDB record for traceability.
 *
 * <p>Requirements: 4.3, 5.2
 */
public record InsertResult(List<String> requestIds) {}
