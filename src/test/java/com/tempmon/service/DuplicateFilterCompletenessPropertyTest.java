package com.tempmon.service;

import com.tempmon.model.FilterResult;
import com.tempmon.model.HygrometerReading;
import com.tempmon.model.ReadingItem;
import com.tempmon.model.SkippedReading;
import net.jqwik.api.*;
import net.jqwik.api.Tuple.Tuple2;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPage;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPageIterable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 9: Duplicate filter completeness.
 *
 * <p>For any payload where one or more readings share {@code timestamp} + {@code location}
 * with an existing database record, the {@code DuplicateFilter} SHALL include a
 * {@code SkippedReading} entry for every such reading in the {@code FilterResult}, and the
 * {@code readingsToInsert} list SHALL contain no reading whose {@code timestamp} + {@code location}
 * matches any existing record. The union of {@code readingsToInsert} indices and {@code skipped}
 * indices covers all N input readings.
 *
 * <p><b>Validates: Requirements 10.3, 10.4, 10.5</b>
 */
@Tag("Feature: json-http-ingestion")
@Tag("Property 9: duplicate filter completeness")
class DuplicateFilterCompletenessPropertyTest {

    private static final TableSchema<ReadingItem> TABLE_SCHEMA =
            TableSchema.fromBean(ReadingItem.class);

    /**
     * Generator: arbitrary lists of HygrometerReading objects where a random subset share
     * timestamp + location with pre-seeded "existing" records returned by a mocked batchGetItem.
     *
     * Assertion: readingsToInsert contains no reading whose (timestamp, location) matches any
     * existing record; skipped contains exactly one entry for every duplicate reading; the union
     * of readingsToInsert indices and skipped indices covers all N input readings.
     */
    @Property(tries = 200)
    void duplicateFilterPartitionsReadingsCompletely(
            @ForAll("readingsWithExistingRecords") Tuple2<List<HygrometerReading>, Map<String, ReadingItem>> input) {

        List<HygrometerReading> readings = input.get1();
        Map<String, ReadingItem> existingRecords = input.get2();

        // Set up mocks
        @SuppressWarnings("unchecked")
        DynamoDbTable<ReadingItem> mockTable = mock(DynamoDbTable.class);
        DynamoDbEnhancedClient mockClient = mock(DynamoDbEnhancedClient.class);

        when(mockTable.tableSchema()).thenReturn(TABLE_SCHEMA);
        when(mockTable.tableName()).thenReturn("hygrometer_readings");

        // Mock batchGetItem to return the pre-seeded existing records
        configureBatchGetItemMock(mockClient, mockTable, existingRecords);

        DuplicateFilter filter = new DuplicateFilter(mockClient, mockTable);

        // Act
        FilterResult result = filter.filter(readings);

        // Collect the composite keys of existing records for comparison
        Set<String> existingKeys = existingRecords.keySet();

        // Assertion 1: readingsToInsert contains no reading whose (timestamp, location)
        // matches any existing record
        for (HygrometerReading reading : result.readingsToInsert()) {
            String key = compositeKey(reading.location(), reading.timestamp());
            assertThat(existingKeys)
                    .as("readingsToInsert should not contain any reading matching an existing DB record key: %s", key)
                    .doesNotContain(key);
        }

        // Assertion 2: skipped contains exactly one entry for every reading that is a duplicate
        // (either intra-batch duplicate or inter-batch duplicate)
        Set<String> seenKeys = new HashSet<>();
        Set<Integer> expectedSkippedIndices = new HashSet<>();

        for (int i = 0; i < readings.size(); i++) {
            HygrometerReading reading = readings.get(i);
            String key = compositeKey(reading.location(), reading.timestamp());

            if (seenKeys.contains(key)) {
                // Intra-batch duplicate — must be skipped
                expectedSkippedIndices.add(i);
            } else {
                seenKeys.add(key);
                // First occurrence — check if it matches an existing DB record
                if (existingKeys.contains(key)) {
                    expectedSkippedIndices.add(i);
                }
            }
        }

        Set<Integer> actualSkippedIndices = result.skipped().stream()
                .map(SkippedReading::index)
                .collect(Collectors.toSet());

        assertThat(actualSkippedIndices)
                .as("skipped should contain exactly one entry for every duplicate reading")
                .isEqualTo(expectedSkippedIndices);

        // Assertion 3: The union of readingsToInsert indices and skipped indices covers all N input readings
        // readingsToInsert.size() + skipped.size() must equal total input readings
        assertThat(result.readingsToInsert().size() + result.skipped().size())
                .as("readingsToInsert.size() + skipped.size() must equal total input readings")
                .isEqualTo(readings.size());

        // Verify no index overlap: skipped indices should not overlap with inserted indices
        Set<Integer> allIndices = IntStream.range(0, readings.size())
                .boxed()
                .collect(Collectors.toSet());

        Set<Integer> insertedIndices = new HashSet<>(allIndices);
        insertedIndices.removeAll(actualSkippedIndices);

        Set<Integer> union = new HashSet<>(actualSkippedIndices);
        union.addAll(insertedIndices);
        assertThat(union)
                .as("Union of readingsToInsert indices and skipped indices must cover all N input readings")
                .isEqualTo(allIndices);
    }

    @Provide
    Arbitrary<Tuple2<List<HygrometerReading>, Map<String, ReadingItem>>> readingsWithExistingRecords() {
        return Arbitraries.integers().between(1, 20).flatMap(size ->
                validReading().list().ofSize(size).flatMap(readings -> {
                    // Determine which readings will have "existing" DB records
                    // Pick a random subset of unique (timestamp, location) keys to be "existing"
                    LinkedHashMap<String, HygrometerReading> uniqueByKey = new LinkedHashMap<>();
                    for (HygrometerReading r : readings) {
                        String key = compositeKey(r.location(), r.timestamp());
                        uniqueByKey.putIfAbsent(key, r);
                    }

                    List<Map.Entry<String, HygrometerReading>> uniqueEntries = new ArrayList<>(uniqueByKey.entrySet());
                    int maxExisting = uniqueEntries.size();

                    // Generate a random count of how many unique keys will be "existing" in DB
                    return Arbitraries.integers().between(0, maxExisting).flatMap(existingCount -> {
                        if (existingCount == 0 || maxExisting == 0) {
                            return Arbitraries.just(Tuple.of(readings, Collections.<String, ReadingItem>emptyMap()));
                        }

                        return Arbitraries.of(uniqueEntries)
                                .set().ofMinSize(1).ofMaxSize(existingCount)
                                .flatMap(selectedEntries -> {
                                    // For each selected entry, decide if it's an exact match or conflicting
                                    return Arbitraries.integers().between(0, 1)
                                            .list().ofSize(selectedEntries.size())
                                            .map(matchTypes -> {
                                                Map<String, ReadingItem> existingMap = new HashMap<>();
                                                int idx = 0;
                                                for (Map.Entry<String, HygrometerReading> entry : selectedEntries) {
                                                    HygrometerReading reading = entry.getValue();
                                                    ReadingItem item = new ReadingItem();
                                                    item.setLocation(reading.location());
                                                    item.setTimestamp(formatTimestamp(reading.timestamp()));

                                                    if (matchTypes.get(idx) == 0) {
                                                        // Exact match — same values
                                                        item.setTemperatureF(reading.temperatureF());
                                                        item.setHumidityPct(reading.humidityPct());
                                                    } else {
                                                        // Conflicting — different values
                                                        item.setTemperatureF(reading.temperatureF().add(BigDecimal.ONE));
                                                        item.setHumidityPct(reading.humidityPct());
                                                    }
                                                    item.setRequestId(UUID.randomUUID().toString());
                                                    item.setIngestedAt("2024-01-01T00:00:00.000Z");

                                                    existingMap.put(entry.getKey(), item);
                                                    idx++;
                                                }
                                                return Tuple.of(readings, existingMap);
                                            });
                                });
                    });
                })
        );
    }

    private Arbitrary<HygrometerReading> validReading() {
        // Use a small set of locations and timestamps to increase duplicate probability
        Arbitrary<OffsetDateTime> timestamps = Arbitraries.longs()
                .between(1_700_000_000L, 1_700_000_100L) // narrow range to increase collisions
                .map(epoch -> OffsetDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC));

        Arbitrary<BigDecimal> temperatures = Arbitraries.bigDecimals()
                .between(new BigDecimal("-100"), new BigDecimal("200"))
                .ofScale(1);

        Arbitrary<BigDecimal> humidities = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("100"))
                .ofScale(1);

        // Small set of locations to increase collision probability
        Arbitrary<String> locations = Arbitraries.of("Kitchen", "Garage", "Bedroom", "Office");

        return Combinators.combine(timestamps, temperatures, humidities, locations)
                .as(HygrometerReading::new);
    }

    /**
     * Configures the mock DynamoDbEnhancedClient to return the appropriate existing records
     * when batchGetItem is called.
     */
    @SuppressWarnings("unchecked")
    private void configureBatchGetItemMock(
            DynamoDbEnhancedClient mockClient,
            DynamoDbTable<ReadingItem> mockTable,
            Map<String, ReadingItem> existingRecords) {

        BatchGetResultPageIterable mockIterable = mock(BatchGetResultPageIterable.class);
        BatchGetResultPage mockPage = mock(BatchGetResultPage.class);

        List<ReadingItem> items = new ArrayList<>(existingRecords.values());
        when(mockPage.resultsForTable(mockTable)).thenReturn(items);
        when(mockIterable.iterator()).thenReturn(List.of(mockPage).iterator());

        when(mockClient.batchGetItem(any(BatchGetItemEnhancedRequest.class)))
                .thenReturn(mockIterable);
    }

    private String compositeKey(String location, OffsetDateTime timestamp) {
        return location + "|" + formatTimestamp(timestamp);
    }

    private String formatTimestamp(OffsetDateTime timestamp) {
        return timestamp.atZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
