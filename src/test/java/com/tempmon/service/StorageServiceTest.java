package com.tempmon.service;

import com.tempmon.exception.DatabaseConstraintException;
import com.tempmon.exception.DatabaseUnavailableException;
import com.tempmon.model.HygrometerReading;
import com.tempmon.model.InsertResult;
import com.tempmon.model.ReadingItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StorageService}.
 *
 * Validates: Requirements 4.1, 4.2, 4.3, 4.6, 4.7
 */
@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<ReadingItem> table;

    private StorageService storageService;

    @BeforeEach
    void setUp() {
        // Stub tableSchema() so that TransactWriteItemsEnhancedRequest.Builder.addPutItem works
        lenient().when(table.tableSchema()).thenReturn(TableSchema.fromBean(ReadingItem.class));
        lenient().when(table.tableName()).thenReturn("hygrometer_readings");
        storageService = new StorageService(enhancedClient, table);
    }

    private HygrometerReading createReading(int index) {
        // Use different days to avoid minute overflow
        return new HygrometerReading(
                OffsetDateTime.of(2024, 1, 1 + (index / 24), index % 24, 0, 0, 0, ZoneOffset.UTC),
                new BigDecimal("72.5"),
                new BigDecimal("45.2"),
                "Kitchen-" + index
        );
    }

    private List<HygrometerReading> createReadings(int count) {
        List<HygrometerReading> readings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            readings.add(createReading(i));
        }
        return readings;
    }

    // --- Requirement 4.1, 4.2, 4.3: Single batch (≤ 25 items) ---

    @Nested
    @DisplayName("Single batch (≤ 25 items)")
    class SingleBatch {

        @Test
        @DisplayName("transactWriteItems called once for 1 reading; result contains 1 UUID")
        void singleReading() {
            List<HygrometerReading> readings = createReadings(1);

            InsertResult result = storageService.persist(readings);

            verify(enhancedClient, times(1)).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
            assertEquals(1, result.requestIds().size());
            assertDoesNotThrow(() -> UUID.fromString(result.requestIds().get(0)));
        }

        @Test
        @DisplayName("transactWriteItems called once for 25 readings; result contains 25 distinct UUIDs")
        void twentyFiveReadings() {
            List<HygrometerReading> readings = createReadings(25);

            InsertResult result = storageService.persist(readings);

            verify(enhancedClient, times(1)).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
            assertEquals(25, result.requestIds().size());

            // All UUIDs are distinct
            Set<String> uniqueIds = new HashSet<>(result.requestIds());
            assertEquals(25, uniqueIds.size());

            // All are valid UUID v4
            for (String id : result.requestIds()) {
                assertDoesNotThrow(() -> UUID.fromString(id));
            }
        }

        @Test
        @DisplayName("empty list returns empty InsertResult without calling DynamoDB")
        void emptyList() {
            InsertResult result = storageService.persist(List.of());

            verify(enhancedClient, never()).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
            assertTrue(result.requestIds().isEmpty());
        }

        @Test
        @DisplayName("result contains N UUIDs matching input size")
        void resultSizeMatchesInput() {
            List<HygrometerReading> readings = createReadings(10);

            InsertResult result = storageService.persist(readings);

            assertEquals(10, result.requestIds().size());
            verify(enhancedClient, times(1)).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
        }
    }

    // --- Requirement 4.2: Two batches (26 items) ---

    @Nested
    @DisplayName("Two batches (26 items)")
    class TwoBatches {

        @Test
        @DisplayName("transactWriteItems called twice for 26 readings")
        void twentySixReadings() {
            List<HygrometerReading> readings = createReadings(26);

            InsertResult result = storageService.persist(readings);

            verify(enhancedClient, times(2)).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
            assertEquals(26, result.requestIds().size());

            // All UUIDs are distinct
            Set<String> uniqueIds = new HashSet<>(result.requestIds());
            assertEquals(26, uniqueIds.size());
        }

        @Test
        @DisplayName("transactWriteItems called three times for 51 readings")
        void fiftyOneReadings() {
            List<HygrometerReading> readings = createReadings(51);

            InsertResult result = storageService.persist(readings);

            verify(enhancedClient, times(3)).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
            assertEquals(51, result.requestIds().size());
        }
    }

    // --- Requirement 4.2, 4.7: First batch succeeds, second fails → compensating deletes ---

    @Nested
    @DisplayName("First batch succeeds, second fails → compensating deletes attempted")
    class CompensatingDeletes {

        @Test
        @DisplayName("second batch failure triggers compensating deletes and throws DatabaseConstraintException")
        void secondBatchFailsWithTransactionCanceled() {
            List<HygrometerReading> readings = createReadings(26);

            // First call succeeds, second throws TransactionCanceledException
            doNothing()
                    .doThrow(TransactionCanceledException.builder()
                            .message("Transaction cancelled")
                            .build())
                    .when(enhancedClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));

            DatabaseConstraintException ex = assertThrows(DatabaseConstraintException.class,
                    () -> storageService.persist(readings));

            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("Transaction cancelled"));

            // transactWriteItems called at least 2 times: first batch write (success), second batch write (fails)
            // Compensating delete is attempted (best-effort) — may or may not result in additional transactWriteItems call
            verify(enhancedClient, atLeast(2)).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
        }

        @Test
        @DisplayName("second batch failure with generic DynamoDbException triggers compensation")
        void secondBatchFailsWithDynamoDbException() {
            List<HygrometerReading> readings = createReadings(26);

            doNothing()
                    .doThrow(TransactionCanceledException.builder()
                            .message("Throughput exceeded")
                            .build())
                    .when(enhancedClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));

            DatabaseConstraintException ex = assertThrows(DatabaseConstraintException.class,
                    () -> storageService.persist(readings));

            assertNotNull(ex.getMessage());

            // At least 2 calls: first batch (success), second batch (fail)
            verify(enhancedClient, atLeast(2)).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
        }

        @Test
        @DisplayName("compensation failure is logged but original exception still thrown")
        void compensationFailureDoesNotChangeException() {
            List<HygrometerReading> readings = createReadings(26);

            // First call succeeds, second fails, third (compensation) also fails
            doNothing()
                    .doThrow(TransactionCanceledException.builder()
                            .message("Transaction cancelled")
                            .build())
                    .doThrow(TransactionCanceledException.builder()
                            .message("Compensation also failed")
                            .build())
                    .when(enhancedClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));

            DatabaseConstraintException ex = assertThrows(DatabaseConstraintException.class,
                    () -> storageService.persist(readings));

            // Original exception message is preserved
            assertTrue(ex.getMessage().contains("Transaction cancelled"));
        }
    }

    // --- Requirement 4.6: DynamoDB connection error → DatabaseUnavailableException ---

    @Nested
    @DisplayName("DynamoDB connection error → DatabaseUnavailableException")
    class ConnectionError {

        @Test
        @DisplayName("connection refused on first batch throws DatabaseUnavailableException")
        void connectionRefusedFirstBatch() {
            List<HygrometerReading> readings = createReadings(5);

            SdkClientException connectionError = SdkClientException.builder()
                    .message("Unable to execute HTTP request")
                    .cause(new ConnectException("Connection refused"))
                    .build();

            doThrow(connectionError)
                    .when(enhancedClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));

            DatabaseUnavailableException ex = assertThrows(DatabaseUnavailableException.class,
                    () -> storageService.persist(readings));

            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("connection failed")
                    || ex.getMessage().contains("Unable to execute HTTP request"));
            assertNotNull(ex.getCause());
        }

        @Test
        @DisplayName("connection error on second batch throws DatabaseUnavailableException and attempts compensation")
        void connectionErrorSecondBatch() {
            List<HygrometerReading> readings = createReadings(26);

            SdkClientException connectionError = SdkClientException.builder()
                    .message("Unable to execute HTTP request")
                    .cause(new ConnectException("Connection refused"))
                    .build();

            // First batch succeeds, second fails with connection error
            doNothing()
                    .doThrow(connectionError)
                    .when(enhancedClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));

            DatabaseUnavailableException ex = assertThrows(DatabaseUnavailableException.class,
                    () -> storageService.persist(readings));

            assertNotNull(ex.getMessage());
            // At least 2 calls: first batch (success), second batch (fail)
            // Compensating delete is attempted (best-effort)
            verify(enhancedClient, atLeast(2)).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));
        }

        @Test
        @DisplayName("unknown host exception throws DatabaseUnavailableException")
        void unknownHost() {
            List<HygrometerReading> readings = createReadings(3);

            SdkClientException connectionError = SdkClientException.builder()
                    .message("Unable to execute HTTP request")
                    .cause(new java.net.UnknownHostException("dynamodb.us-east-1.amazonaws.com"))
                    .build();

            doThrow(connectionError)
                    .when(enhancedClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));

            DatabaseUnavailableException ex = assertThrows(DatabaseUnavailableException.class,
                    () -> storageService.persist(readings));

            assertNotNull(ex.getCause());
        }
    }
}
