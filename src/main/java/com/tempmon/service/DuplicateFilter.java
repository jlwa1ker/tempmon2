package com.tempmon.service;

import com.tempmon.model.FilterResult;
import com.tempmon.model.HygrometerReading;
import com.tempmon.model.ReadingItem;
import com.tempmon.model.SkippedReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPage;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Filters duplicate readings from a validated batch before persistence.
 *
 * <p>Performs two levels of duplicate detection:
 * <ol>
 *   <li><b>Intra-batch</b>: readings within the same payload that share
 *       {@code timestamp} + {@code location}. The first occurrence is kept;
 *       subsequent occurrences are skipped with reason {@code "conflicting_data"}
 *       and a WARNING is logged.</li>
 *   <li><b>Inter-batch</b>: candidate readings whose {@code (location, timestamp)}
 *       key already exists in DynamoDB. Exact matches (all field values identical)
 *       are silently skipped with reason {@code "exact_match"}. Conflicting matches
 *       (any value differs) are skipped with reason {@code "conflicting_data"} and
 *       a WARNING is logged.</li>
 * </ol>
 *
 * <p>Requirements: 10.1, 10.2, 10.3, 10.4, 10.5
 */
@Service
public class DuplicateFilter {

    private static final Logger log = LoggerFactory.getLogger(DuplicateFilter.class);

    /**
     * DynamoDB BatchGetItem limit per request.
     */
    private static final int BATCH_GET_LIMIT = 100;

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<ReadingItem> table;

    public DuplicateFilter(DynamoDbEnhancedClient enhancedClient,
                           DynamoDbTable<ReadingItem> table) {
        this.enhancedClient = enhancedClient;
        this.table = table;
    }

    /**
     * Filters the given list of validated readings, removing intra-batch and
     * inter-batch duplicates.
     *
     * @param readings the validated readings from the payload
     * @return a {@link FilterResult} partitioning readings into those to insert
     *         and those that were skipped
     */
    public FilterResult filter(List<HygrometerReading> readings) {
        List<SkippedReading> skipped = new ArrayList<>();

        // Phase 1: Intra-batch duplicate detection
        // LinkedHashMap preserves insertion order; value is the original index
        LinkedHashMap<String, CandidateEntry> candidates = new LinkedHashMap<>();

        for (int i = 0; i < readings.size(); i++) {
            HygrometerReading reading = readings.get(i);
            String key = compositeKey(reading.location(), reading.timestamp());

            if (candidates.containsKey(key)) {
                // Intra-batch duplicate — always skip with WARNING
                log.warn("Intra-batch duplicate at index {}: timestamp={}, location={}",
                        i, formatTimestamp(reading.timestamp()), reading.location());
                skipped.add(new SkippedReading(i, "conflicting_data"));
            } else {
                candidates.put(key, new CandidateEntry(reading, i));
            }
        }

        if (candidates.isEmpty()) {
            return new FilterResult(List.of(), skipped);
        }

        // Phase 2: Inter-batch duplicate detection via DynamoDB BatchGetItem
        Map<String, ReadingItem> existingRecords = fetchExistingRecords(candidates);

        List<HygrometerReading> readingsToInsert = new ArrayList<>();

        for (Map.Entry<String, CandidateEntry> entry : candidates.entrySet()) {
            String key = entry.getKey();
            CandidateEntry candidate = entry.getValue();
            ReadingItem existing = existingRecords.get(key);

            if (existing == null) {
                // No existing record — keep for insertion
                readingsToInsert.add(candidate.reading());
            } else if (isExactMatch(candidate.reading(), existing)) {
                // Exact-match inter-batch duplicate — silent skip
                skipped.add(new SkippedReading(candidate.index(), "exact_match"));
            } else {
                // Conflicting inter-batch duplicate — skip with WARNING
                log.warn("Conflicting inter-batch duplicate at index {}: timestamp={}, location={}",
                        candidate.index(),
                        formatTimestamp(candidate.reading().timestamp()),
                        candidate.reading().location());
                skipped.add(new SkippedReading(candidate.index(), "conflicting_data"));
            }
        }

        return new FilterResult(readingsToInsert, skipped);
    }

    /**
     * Fetches existing DynamoDB records for all candidate keys, issuing multiple
     * round-trips if there are more than {@value #BATCH_GET_LIMIT} candidates.
     */
    private Map<String, ReadingItem> fetchExistingRecords(
            LinkedHashMap<String, CandidateEntry> candidates) {

        Map<String, ReadingItem> result = new HashMap<>();
        List<Map.Entry<String, CandidateEntry>> entries = new ArrayList<>(candidates.entrySet());

        for (int start = 0; start < entries.size(); start += BATCH_GET_LIMIT) {
            int end = Math.min(start + BATCH_GET_LIMIT, entries.size());
            List<Map.Entry<String, CandidateEntry>> batch = entries.subList(start, end);

            ReadBatch.Builder<ReadingItem> readBatchBuilder =
                    ReadBatch.builder(ReadingItem.class).mappedTableResource(table);

            for (Map.Entry<String, CandidateEntry> entry : batch) {
                CandidateEntry candidate = entry.getValue();
                String location = candidate.reading().location();
                String timestamp = formatTimestamp(candidate.reading().timestamp());
                readBatchBuilder.addGetItem(
                        Key.builder()
                                .partitionValue(location)
                                .sortValue(timestamp)
                                .build());
            }

            BatchGetItemEnhancedRequest request = BatchGetItemEnhancedRequest.builder()
                    .readBatches(readBatchBuilder.build())
                    .build();

            for (BatchGetResultPage page : enhancedClient.batchGetItem(request)) {
                List<ReadingItem> items = page.resultsForTable(table);
                for (ReadingItem item : items) {
                    String key = compositeKey(item.getLocation(), item.getTimestamp());
                    result.put(key, item);
                }
            }
        }

        return result;
    }

    /**
     * Checks whether a candidate reading is an exact match with an existing
     * DynamoDB record (same temperature_f and humidity_pct values).
     */
    private boolean isExactMatch(HygrometerReading reading, ReadingItem existing) {
        return reading.temperatureF().compareTo(existing.getTemperatureF()) == 0
                && reading.humidityPct().compareTo(existing.getHumidityPct()) == 0;
    }

    /**
     * Creates a composite key string from location and timestamp for map lookups.
     */
    private String compositeKey(String location, OffsetDateTime timestamp) {
        return location + "|" + formatTimestamp(timestamp);
    }

    /**
     * Creates a composite key string from location and a pre-formatted timestamp string.
     */
    private String compositeKey(String location, String timestamp) {
        return location + "|" + timestamp;
    }

    /**
     * Formats an {@link OffsetDateTime} to UTC ISO 8601 string for DynamoDB key matching.
     */
    private String formatTimestamp(OffsetDateTime timestamp) {
        return timestamp.atZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * Internal record holding a candidate reading and its original index in the payload.
     */
    private record CandidateEntry(HygrometerReading reading, int index) {}
}
