package com.tempmon.service;

import com.tempmon.model.ReadingItem;
import net.jqwik.api.*;
import net.jqwik.api.Tuple.Tuple2;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 6: Query result ordering and bounds
 *
 * Generator: arbitrary sets of ReadingItem records pre-loaded into DynamoDB Local;
 *            arbitrary start, end, locations parameters
 * Assertion: every returned record has timestamp in [start, end] and location in the
 *            requested set; records are sorted ascending by timestamp; count ≤ 10,000;
 *            truncated is correct
 *
 * Validates: Requirements 8.7, 8.8
 */
@Tag("Feature: json-http-ingestion")
@Tag("Property 6: query result ordering and bounds")
class QueryHandlerOrderingBoundsPropertyTest {

    /**
     * Property: Every returned record has timestamp within [start, end], location in the
     * requested set, results are sorted ascending by timestamp, count ≤ 10,000, and
     * truncated flag is correct.
     *
     * Uses a mock-based approach that simulates DynamoDB query behavior by mocking the
     * DynamoDbTable to return controlled sets of ReadingItem objects.
     *
     * Validates: Requirements 8.7, 8.8
     */
    @Property(tries = 200)
    void queryResultsAreWithinBoundsAndSorted(
            @ForAll("queryScenarios") QueryScenario scenario) {

        @SuppressWarnings("unchecked")
        DynamoDbTable<ReadingItem> mockTable = mock(DynamoDbTable.class);

        // QueryHandler queries one location at a time in order.
        // We simulate DynamoDB behavior: for each location, return items matching
        // that location whose timestamp is between start and end (inclusive).
        String startKey = toSortKeyString(scenario.start);
        String endKey = toSortKeyString(scenario.end);

        List<String> locationOrder = new ArrayList<>(scenario.queryLocations);
        final int[] callIndex = {0};

        when(mockTable.query(any(QueryEnhancedRequest.class))).thenAnswer(invocation -> {
            if (callIndex[0] >= locationOrder.size()) {
                return mockPageIterable(Collections.emptyList());
            }
            String currentLocation = locationOrder.get(callIndex[0]);
            callIndex[0]++;

            // Simulate DynamoDB: return items for this location within the time range
            List<ReadingItem> locationItems = scenario.allRecords.stream()
                    .filter(item -> item.getLocation().equals(currentLocation))
                    .filter(item -> item.getTimestamp().compareTo(startKey) >= 0
                            && item.getTimestamp().compareTo(endKey) <= 0)
                    .collect(Collectors.toList());

            return mockPageIterable(locationItems);
        });

        // Execute the query
        QueryHandler handler = new QueryHandler(mockTable);
        QueryHandler.QueryResult result = handler.query(scenario.start, scenario.end, locationOrder);

        // Assertions
        List<ReadingItem> readings = result.readings();

        // 1. Every returned record has timestamp in [start, end]
        for (ReadingItem item : readings) {
            assertThat(item.getTimestamp())
                    .as("timestamp should be >= start")
                    .isGreaterThanOrEqualTo(startKey);
            assertThat(item.getTimestamp())
                    .as("timestamp should be <= end")
                    .isLessThanOrEqualTo(endKey);
        }

        // 2. Every returned record has location in the requested set
        for (ReadingItem item : readings) {
            assertThat(scenario.queryLocations)
                    .as("location should be in the requested set")
                    .contains(item.getLocation());
        }

        // 3. Records are sorted ascending by timestamp
        for (int i = 1; i < readings.size(); i++) {
            assertThat(readings.get(i).getTimestamp())
                    .as("records should be sorted ascending by timestamp at index " + i)
                    .isGreaterThanOrEqualTo(readings.get(i - 1).getTimestamp());
        }

        // 4. Count ≤ 10,000
        assertThat(readings.size())
                .as("result count should be at most 10,000")
                .isLessThanOrEqualTo(10_000);

        // 5. truncated is correct
        long totalMatchingCount = scenario.allRecords.stream()
                .filter(item -> scenario.queryLocations.contains(item.getLocation()))
                .filter(item -> item.getTimestamp().compareTo(startKey) >= 0
                        && item.getTimestamp().compareTo(endKey) <= 0)
                .count();

        if (totalMatchingCount > 10_000) {
            assertThat(result.truncated())
                    .as("truncated should be true when results exceed 10,000")
                    .isTrue();
            assertThat(readings.size()).isEqualTo(10_000);
        } else {
            assertThat(result.truncated())
                    .as("truncated should be false when results do not exceed 10,000")
                    .isFalse();
            assertThat((long) readings.size()).isEqualTo(totalMatchingCount);
        }
    }

    /**
     * Property: When the total result set exceeds 10,000 records, truncated is true
     * and exactly 10,000 records are returned.
     *
     * Validates: Requirements 8.8
     */
    @Property(tries = 5)
    void truncationWorksCorrectlyForLargeResultSets(
            @ForAll("truncationScenarios") QueryScenario scenario) {

        @SuppressWarnings("unchecked")
        DynamoDbTable<ReadingItem> mockTable = mock(DynamoDbTable.class);

        String startKey = toSortKeyString(scenario.start);
        String endKey = toSortKeyString(scenario.end);
        List<String> locationOrder = new ArrayList<>(scenario.queryLocations);
        final int[] callIndex = {0};

        when(mockTable.query(any(QueryEnhancedRequest.class))).thenAnswer(invocation -> {
            if (callIndex[0] >= locationOrder.size()) {
                return mockPageIterable(Collections.emptyList());
            }
            String currentLocation = locationOrder.get(callIndex[0]);
            callIndex[0]++;

            List<ReadingItem> locationItems = scenario.allRecords.stream()
                    .filter(item -> item.getLocation().equals(currentLocation))
                    .filter(item -> item.getTimestamp().compareTo(startKey) >= 0
                            && item.getTimestamp().compareTo(endKey) <= 0)
                    .collect(Collectors.toList());

            return mockPageIterable(locationItems);
        });

        QueryHandler handler = new QueryHandler(mockTable);
        QueryHandler.QueryResult result = handler.query(scenario.start, scenario.end, locationOrder);

        // With > 10,000 records, truncated must be true and exactly 10,000 returned
        assertThat(result.truncated()).isTrue();
        assertThat(result.readings().size()).isEqualTo(10_000);

        // Still sorted
        for (int i = 1; i < result.readings().size(); i++) {
            assertThat(result.readings().get(i).getTimestamp())
                    .isGreaterThanOrEqualTo(result.readings().get(i - 1).getTimestamp());
        }
    }

    // --- Generators ---

    @Provide
    Arbitrary<QueryScenario> queryScenarios() {
        return Combinators.combine(
                locations(),
                timeRanges(),
                Arbitraries.integers().between(0, 30)
        ).as((locs, range, extraCount) -> {
            List<String> queryLocations = locs;
            OffsetDateTime start = range.get1();
            OffsetDateTime end = range.get2();

            List<ReadingItem> allRecords = new ArrayList<>();
            Random rng = new Random();

            // Generate records within the time range for queried locations
            int withinCount = rng.nextInt(15) + 1;
            for (int i = 0; i < withinCount; i++) {
                String loc = queryLocations.get(rng.nextInt(queryLocations.size()));
                OffsetDateTime ts = randomTimeBetween(start, end, rng);
                allRecords.add(createReadingItem(loc, ts, rng));
            }

            // Generate records outside the time range (before start or after end)
            for (int i = 0; i < extraCount; i++) {
                String loc = queryLocations.get(rng.nextInt(queryLocations.size()));
                OffsetDateTime ts;
                if (rng.nextBoolean()) {
                    ts = start.minusHours(rng.nextInt(100) + 1);
                } else {
                    ts = end.plusHours(rng.nextInt(100) + 1);
                }
                allRecords.add(createReadingItem(loc, ts, rng));
            }

            // Generate records for locations NOT in the query set
            for (int i = 0; i < extraCount / 2; i++) {
                String otherLoc = "OtherLocation" + i;
                OffsetDateTime ts = randomTimeBetween(start, end, rng);
                allRecords.add(createReadingItem(otherLoc, ts, rng));
            }

            return new QueryScenario(allRecords, queryLocations, start, end);
        });
    }

    @Provide
    Arbitrary<QueryScenario> truncationScenarios() {
        return Combinators.combine(
                locations(),
                timeRanges()
        ).as((locs, range) -> {
            List<String> queryLocations = locs;
            OffsetDateTime start = range.get1();
            OffsetDateTime end = range.get2();

            // Generate > 10,000 records within the time range
            List<ReadingItem> allRecords = new ArrayList<>();
            Random rng = new Random(42);
            int totalRecords = 10_050;

            for (int i = 0; i < totalRecords; i++) {
                String loc = queryLocations.get(i % queryLocations.size());
                OffsetDateTime ts = randomTimeBetween(start, end, rng);
                allRecords.add(createReadingItem(loc, ts, rng));
            }

            return new QueryScenario(allRecords, queryLocations, start, end);
        });
    }

    private Arbitrary<List<String>> locations() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(15)
                .list()
                .ofMinSize(1)
                .ofMaxSize(4)
                .map(list -> list.stream().distinct().collect(Collectors.toList()))
                .filter(list -> !list.isEmpty());
    }

    private Arbitrary<Tuple2<OffsetDateTime, OffsetDateTime>> timeRanges() {
        return Arbitraries.longs()
                .between(1_000_000_000L, 1_900_000_000L)
                .flatMap(startEpoch -> {
                    OffsetDateTime start = OffsetDateTime.ofInstant(
                            Instant.ofEpochSecond(startEpoch), ZoneOffset.UTC);
                    return Arbitraries.longs()
                            .between(3600L, 86400L * 7) // 1 hour to 7 days duration
                            .map(duration -> {
                                OffsetDateTime end = start.plusSeconds(duration);
                                return Tuple.of(start, end);
                            });
                });
    }

    // --- Helpers ---

    private OffsetDateTime randomTimeBetween(OffsetDateTime start, OffsetDateTime end, Random rng) {
        long startEpoch = start.toEpochSecond();
        long endEpoch = end.toEpochSecond();
        if (endEpoch <= startEpoch) {
            return start;
        }
        long randomEpoch = startEpoch + (long) (rng.nextDouble() * (endEpoch - startEpoch));
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(randomEpoch), ZoneOffset.UTC);
    }

    private ReadingItem createReadingItem(String location, OffsetDateTime timestamp, Random rng) {
        ReadingItem item = new ReadingItem();
        item.setLocation(location);
        item.setTimestamp(toSortKeyString(timestamp));
        item.setRequestId(UUID.randomUUID().toString());
        item.setTemperatureF(BigDecimal.valueOf(rng.nextDouble() * 300 - 100)
                .setScale(2, RoundingMode.HALF_UP));
        item.setHumidityPct(BigDecimal.valueOf(rng.nextDouble() * 100)
                .setScale(2, RoundingMode.HALF_UP));
        item.setIngestedAt(OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return item;
    }

    private String toSortKeyString(OffsetDateTime dateTime) {
        return dateTime.atZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    @SuppressWarnings("unchecked")
    private PageIterable<ReadingItem> mockPageIterable(List<ReadingItem> items) {
        PageIterable<ReadingItem> pageIterable = mock(PageIterable.class);
        SdkIterable<ReadingItem> sdkIterable = () -> items.iterator();
        when(pageIterable.items()).thenReturn(sdkIterable);
        return pageIterable;
    }

    // --- Test data record ---

    record QueryScenario(
            List<ReadingItem> allRecords,
            List<String> queryLocations,
            OffsetDateTime start,
            OffsetDateTime end
    ) {}
}
