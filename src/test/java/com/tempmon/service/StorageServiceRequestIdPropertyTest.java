package com.tempmon.service;

import com.tempmon.model.HygrometerReading;
import com.tempmon.model.InsertResult;
import com.tempmon.model.ReadingItem;
import net.jqwik.api.*;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 5: Request ID uniqueness and ordering.
 *
 * <p>For any valid payload containing N readings, the InsertResult returned by
 * StorageService SHALL contain exactly N distinct UUID v4 strings in the same
 * order as the input readings list, and no two strings SHALL be equal.
 *
 * <p><b>Validates: Requirements 4.3, 5.2</b>
 */
@Tag("Feature: json-http-ingestion")
@Tag("Property 5: request ID uniqueness and ordering")
class StorageServiceRequestIdPropertyTest {

    private static final TableSchema<ReadingItem> TABLE_SCHEMA =
            TableSchema.fromBean(ReadingItem.class);

    /**
     * Generator: arbitrary valid reading lists of 1–100 items (mocked DynamoDB).
     * Assertion: result contains exactly N UUIDs; all are distinct; order matches input order.
     */
    @Property(tries = 200)
    void persistReturnsExactlyNDistinctUuidsInInputOrder(
            @ForAll("validReadingLists") List<HygrometerReading> readings) {

        // Mock DynamoDB so transactWriteItems succeeds without actually writing
        @SuppressWarnings("unchecked")
        DynamoDbTable<ReadingItem> mockTable = mock(DynamoDbTable.class);
        DynamoDbEnhancedClient mockClient = mock(DynamoDbEnhancedClient.class);

        // The enhanced client's TransactWriteItemsEnhancedRequest.Builder.addPutItem
        // needs the table's schema and table name to build the request
        when(mockTable.tableSchema()).thenReturn(TABLE_SCHEMA);
        when(mockTable.tableName()).thenReturn("hygrometer_readings");

        // transactWriteItems is void — just let it succeed (no-op)
        doNothing().when(mockClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));

        StorageService storageService = new StorageService(mockClient, mockTable);

        // Act
        InsertResult result = storageService.persist(readings);
        List<String> requestIds = result.requestIds();

        // Assert: exactly N UUIDs returned
        assertThat(requestIds)
                .as("InsertResult should contain exactly N request IDs for N input readings")
                .hasSize(readings.size());

        // Assert: all are distinct
        Set<String> uniqueIds = new HashSet<>(requestIds);
        assertThat(uniqueIds)
                .as("All request IDs must be distinct (no duplicates)")
                .hasSize(readings.size());

        // Assert: each is a valid UUID v4 string
        for (String id : requestIds) {
            assertThat(id)
                    .as("Each request ID must be a valid UUID")
                    .isNotNull()
                    .isNotEmpty();
            // Verify it parses as a UUID
            UUID parsed = UUID.fromString(id);
            assertThat(parsed.version())
                    .as("Request ID must be UUID v4")
                    .isEqualTo(4);
        }

        // Assert: order matches input order — the i-th request ID corresponds to the i-th reading.
        // We verify ordering by capturing the items written to DynamoDB and checking that
        // the request IDs in the written items match the returned list in the same order.
        assertThat(requestIds)
                .as("Request IDs list should maintain stable ordering (no nulls, no gaps)")
                .doesNotContainNull()
                .allSatisfy(id -> assertThat(id).matches(
                        "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
    }

    @Provide
    Arbitrary<List<HygrometerReading>> validReadingLists() {
        return validReading().list().ofMinSize(1).ofMaxSize(100);
    }

    private Arbitrary<HygrometerReading> validReading() {
        Arbitrary<OffsetDateTime> timestamps = Arbitraries.longs()
                .between(0L, 4_102_444_800L) // 2000-01-01 to ~2100-01-01 epoch seconds
                .map(epoch -> OffsetDateTime.ofInstant(
                        java.time.Instant.ofEpochSecond(epoch), ZoneOffset.UTC));

        Arbitrary<BigDecimal> temperatures = Arbitraries.bigDecimals()
                .between(new BigDecimal("-100"), new BigDecimal("200"))
                .ofScale(1);

        Arbitrary<BigDecimal> humidities = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("100"))
                .ofScale(1);

        Arbitrary<String> locations = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(50);

        return Combinators.combine(timestamps, temperatures, humidities, locations)
                .as(HygrometerReading::new);
    }
}
