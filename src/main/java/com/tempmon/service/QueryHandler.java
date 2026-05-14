package com.tempmon.service;

import com.tempmon.exception.DatabaseUnavailableException;
import com.tempmon.model.ReadingItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.core.exception.SdkException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Queries DynamoDB for hygrometer readings within a time range and set of locations.
 *
 * <p>Issues one DynamoDB Query per location (PK = location, SK BETWEEN start and end),
 * merges results from all locations, sorts by timestamp ascending, and caps at 10,000 records.
 *
 * <p>Requirements: 8.7, 8.8, 8.9, 8.10
 */
@Service
public class QueryHandler {

    private static final Logger log = LoggerFactory.getLogger(QueryHandler.class);
    private static final int MAX_RESULTS = 10_000;

    private final DynamoDbTable<ReadingItem> readingsTable;

    public QueryHandler(DynamoDbTable<ReadingItem> readingsTable) {
        this.readingsTable = readingsTable;
    }

    /**
     * Queries for readings within the given time range and locations.
     *
     * @param start     inclusive start of the time range
     * @param end       inclusive end of the time range
     * @param locations list of location names to query
     * @return a {@link QueryResult} containing the sorted readings and truncation flag
     * @throws DatabaseUnavailableException if DynamoDB cannot be reached
     */
    public QueryResult query(OffsetDateTime start, OffsetDateTime end, List<String> locations) {
        String startKey = toSortKeyString(start);
        String endKey = toSortKeyString(end);

        List<ReadingItem> allResults = new ArrayList<>();

        try {
            for (String location : locations) {
                QueryConditional queryConditional = QueryConditional.sortBetween(
                        Key.builder().partitionValue(location).sortValue(startKey).build(),
                        Key.builder().partitionValue(location).sortValue(endKey).build()
                );

                QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                        .queryConditional(queryConditional)
                        .build();

                readingsTable.query(request)
                        .items()
                        .forEach(allResults::add);
            }
        } catch (SdkException e) {
            log.error("DynamoDB query failed: {}", e.getMessage(), e);
            throw new DatabaseUnavailableException(
                    "Database unavailable during query: " + e.getMessage(), e);
        }

        // Sort all results by timestamp ascending (ISO 8601 strings sort lexicographically)
        allResults.sort(Comparator.comparing(ReadingItem::getTimestamp));

        // Determine truncation
        boolean truncated = allResults.size() > MAX_RESULTS;

        // Cap at 10,000 records
        List<ReadingItem> results = truncated
                ? allResults.subList(0, MAX_RESULTS)
                : allResults;

        return new QueryResult(results, truncated);
    }

    /**
     * Converts an OffsetDateTime to a UTC ISO 8601 string suitable for sort key comparison.
     */
    private String toSortKeyString(OffsetDateTime dateTime) {
        return dateTime.atZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * Holds the result of a query operation.
     *
     * @param readings  the list of matching readings (sorted by timestamp ascending, capped at 10,000)
     * @param truncated true if the raw result set exceeded 10,000 records
     */
    public record QueryResult(List<ReadingItem> readings, boolean truncated) {}
}
