package com.tempmon.service;

import com.tempmon.model.ReadingItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link QueryHandler}.
 *
 * Validates: Requirements 8.7, 8.8, 8.9
 */
@ExtendWith(MockitoExtension.class)
class QueryHandlerTest {

    @Mock
    private DynamoDbTable<ReadingItem> readingsTable;

    private QueryHandler queryHandler;

    @BeforeEach
    void setUp() {
        queryHandler = new QueryHandler(readingsTable);
    }

    // --- Requirement 8.7, 8.8: Single location, results within range → returned sorted ---

    @Nested
    @DisplayName("Single location query")
    class SingleLocation {

        @Test
        @DisplayName("results within range are returned sorted by timestamp ascending")
        void resultsSortedByTimestamp() {
            OffsetDateTime start = OffsetDateTime.of(2024, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime end = OffsetDateTime.of(2024, 1, 15, 23, 59, 59, 0, ZoneOffset.UTC);

            // Create items out of order to verify sorting
            ReadingItem item1 = createReadingItem("Kitchen", "2024-01-15T10:00:00Z", "72.5", "45.0");
            ReadingItem item2 = createReadingItem("Kitchen", "2024-01-15T08:00:00Z", "68.0", "50.0");
            ReadingItem item3 = createReadingItem("Kitchen", "2024-01-15T14:00:00Z", "75.0", "40.0");

            mockQueryResults(List.of(item1, item2, item3));

            QueryHandler.QueryResult result = queryHandler.query(start, end, List.of("Kitchen"));

            assertFalse(result.truncated());
            assertEquals(3, result.readings().size());
            // Verify sorted ascending by timestamp
            assertEquals("2024-01-15T08:00:00Z", result.readings().get(0).getTimestamp());
            assertEquals("2024-01-15T10:00:00Z", result.readings().get(1).getTimestamp());
            assertEquals("2024-01-15T14:00:00Z", result.readings().get(2).getTimestamp());
        }
    }

    // --- Requirement 8.7, 8.8: Multiple locations → results merged and sorted ---

    @Nested
    @DisplayName("Multiple locations query")
    class MultipleLocations {

        @Test
        @DisplayName("results from multiple locations are merged and sorted by timestamp ascending")
        void mergedAndSorted() {
            OffsetDateTime start = OffsetDateTime.of(2024, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime end = OffsetDateTime.of(2024, 1, 15, 23, 59, 59, 0, ZoneOffset.UTC);

            // Kitchen items
            ReadingItem kitchenItem1 = createReadingItem("Kitchen", "2024-01-15T09:00:00Z", "70.0", "45.0");
            ReadingItem kitchenItem2 = createReadingItem("Kitchen", "2024-01-15T12:00:00Z", "74.0", "42.0");

            // Garage items
            ReadingItem garageItem1 = createReadingItem("Garage", "2024-01-15T08:00:00Z", "55.0", "60.0");
            ReadingItem garageItem2 = createReadingItem("Garage", "2024-01-15T11:00:00Z", "58.0", "55.0");

            // Mock: first call returns Kitchen items, second call returns Garage items
            @SuppressWarnings("unchecked")
            PageIterable<ReadingItem> kitchenPages = mock(PageIterable.class);
            when(kitchenPages.items()).thenReturn(new SdkIterableStub<>(List.of(kitchenItem1, kitchenItem2)));

            @SuppressWarnings("unchecked")
            PageIterable<ReadingItem> garagePages = mock(PageIterable.class);
            when(garagePages.items()).thenReturn(new SdkIterableStub<>(List.of(garageItem1, garageItem2)));

            when(readingsTable.query(any(QueryEnhancedRequest.class)))
                    .thenReturn(kitchenPages)
                    .thenReturn(garagePages);

            QueryHandler.QueryResult result = queryHandler.query(start, end, List.of("Kitchen", "Garage"));

            assertFalse(result.truncated());
            assertEquals(4, result.readings().size());
            // Verify merged and sorted ascending by timestamp
            assertEquals("2024-01-15T08:00:00Z", result.readings().get(0).getTimestamp());
            assertEquals("Garage", result.readings().get(0).getLocation());
            assertEquals("2024-01-15T09:00:00Z", result.readings().get(1).getTimestamp());
            assertEquals("Kitchen", result.readings().get(1).getLocation());
            assertEquals("2024-01-15T11:00:00Z", result.readings().get(2).getTimestamp());
            assertEquals("Garage", result.readings().get(2).getLocation());
            assertEquals("2024-01-15T12:00:00Z", result.readings().get(3).getTimestamp());
            assertEquals("Kitchen", result.readings().get(3).getLocation());
        }
    }

    // --- Requirement 8.8: Result count = 10,000 → truncated = false ---

    @Nested
    @DisplayName("Truncation behavior")
    class Truncation {

        @Test
        @DisplayName("exactly 10,000 results → truncated = false")
        void exactlyAtLimit() {
            OffsetDateTime start = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime end = OffsetDateTime.of(2024, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);

            List<ReadingItem> items = generateItems("Kitchen", 10_000);
            mockQueryResults(items);

            QueryHandler.QueryResult result = queryHandler.query(start, end, List.of("Kitchen"));

            assertFalse(result.truncated());
            assertEquals(10_000, result.readings().size());
        }

        @Test
        @DisplayName("10,001 results → truncated = true, only 10,000 items returned")
        void exceedsLimit() {
            OffsetDateTime start = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime end = OffsetDateTime.of(2024, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);

            List<ReadingItem> items = generateItems("Kitchen", 10_001);
            mockQueryResults(items);

            QueryHandler.QueryResult result = queryHandler.query(start, end, List.of("Kitchen"));

            assertTrue(result.truncated());
            assertEquals(10_000, result.readings().size());
        }
    }

    // --- Requirement 8.9: Empty result → empty list, truncated = false ---

    @Nested
    @DisplayName("Empty result")
    class EmptyResult {

        @Test
        @DisplayName("no matching records → empty list, truncated = false")
        void emptyResult() {
            OffsetDateTime start = OffsetDateTime.of(2024, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime end = OffsetDateTime.of(2024, 1, 15, 23, 59, 59, 0, ZoneOffset.UTC);

            mockQueryResults(List.of());

            QueryHandler.QueryResult result = queryHandler.query(start, end, List.of("Kitchen"));

            assertFalse(result.truncated());
            assertTrue(result.readings().isEmpty());
        }
    }

    // --- Helper methods ---

    private ReadingItem createReadingItem(String location, String timestamp,
                                          String temperatureF, String humidityPct) {
        ReadingItem item = new ReadingItem();
        item.setLocation(location);
        item.setTimestamp(timestamp);
        item.setTemperatureF(new BigDecimal(temperatureF));
        item.setHumidityPct(new BigDecimal(humidityPct));
        item.setRequestId("req-" + timestamp.hashCode());
        item.setIngestedAt("2024-01-15T10:00:00.000Z");
        return item;
    }

    private List<ReadingItem> generateItems(String location, int count) {
        List<ReadingItem> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // Generate unique timestamps that sort correctly
            int day = (i / 86400) + 1;
            int remainder = i % 86400;
            int hour = (remainder / 3600) % 24;
            int minute = (remainder / 60) % 60;
            int second = remainder % 60;
            String timestamp = String.format("2024-%02d-%02dT%02d:%02d:%02dZ",
                    ((day - 1) / 28) + 1, ((day - 1) % 28) + 1, hour, minute, second);

            ReadingItem item = new ReadingItem();
            item.setLocation(location);
            item.setTimestamp(timestamp);
            item.setTemperatureF(new BigDecimal("70.0"));
            item.setHumidityPct(new BigDecimal("50.0"));
            item.setRequestId("req-" + i);
            item.setIngestedAt("2024-01-15T10:00:00.000Z");
            items.add(item);
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private void mockQueryResults(List<ReadingItem> items) {
        PageIterable<ReadingItem> pageIterable = mock(PageIterable.class);
        when(pageIterable.items()).thenReturn(new SdkIterableStub<>(items));
        when(readingsTable.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
    }

    /**
     * A simple stub implementing SdkIterable to provide items from a list.
     * This avoids complex mocking of the DynamoDB pagination API.
     */
    private static class SdkIterableStub<T> implements SdkIterable<T> {

        private final List<T> items;

        SdkIterableStub(List<T> items) {
            this.items = items;
        }

        @Override
        public Iterator<T> iterator() {
            return items.iterator();
        }

        @Override
        public Stream<T> stream() {
            return items.stream();
        }
    }
}
