package com.tempmon.service;

import com.tempmon.exception.DatabaseConstraintException;
import com.tempmon.exception.DatabaseUnavailableException;
import com.tempmon.model.HygrometerReading;
import com.tempmon.model.InsertResult;
import com.tempmon.model.ReadingItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Spring service that persists validated hygrometer readings to DynamoDB.
 *
 * <p>Accepts a list of non-duplicate {@link HygrometerReading} objects (duplicate
 * filtering has already been applied by {@code DuplicateFilter}), assigns a UUID v4
 * {@code request_id} and a UTC ingestion timestamp to each, and writes them in
 * batches of ≤ 25 using {@code transactWriteItems}.
 *
 * <p>Atomicity: either all non-duplicate records are inserted or none are. If any
 * batch fails, previously committed batches are compensated by deleting their
 * inserted items (best-effort).
 *
 * <p>Requirements: 4.1, 4.2, 4.3, 4.4, 4.6, 4.7
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);
    private static final int BATCH_SIZE = 25;
    private static final DateTimeFormatter INGESTED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<ReadingItem> table;

    public StorageService(DynamoDbEnhancedClient enhancedClient, DynamoDbTable<ReadingItem> table) {
        this.enhancedClient = enhancedClient;
        this.table = table;
    }

    /**
     * Persists the given list of validated, non-duplicate readings to DynamoDB.
     *
     * @param readings the list of readings to persist (must be non-null, may be empty)
     * @return an {@link InsertResult} containing the ordered list of request IDs
     * @throws DatabaseUnavailableException if the database cannot be reached
     * @throws DatabaseConstraintException if a transaction is cancelled or another constraint violation occurs
     */
    public InsertResult persist(List<HygrometerReading> readings) {
        if (readings.isEmpty()) {
            return new InsertResult(List.of());
        }

        String ingestedAt = Instant.now().atOffset(ZoneOffset.UTC).format(INGESTED_AT_FORMAT);
        List<ReadingItem> items = new ArrayList<>(readings.size());
        List<String> requestIds = new ArrayList<>(readings.size());

        // Assign request_id and ingestion timestamp to each reading
        for (HygrometerReading reading : readings) {
            String requestId = UUID.randomUUID().toString();
            requestIds.add(requestId);

            ReadingItem item = new ReadingItem();
            item.setLocation(reading.location());
            item.setTimestamp(reading.timestamp().atZoneSameInstant(ZoneOffset.UTC)
                    .toOffsetDateTime().toString());
            item.setRequestId(requestId);
            item.setTemperatureF(reading.temperatureF());
            item.setHumidityPct(reading.humidityPct());
            item.setIngestedAt(ingestedAt);
            items.add(item);
        }

        // Split into batches of ≤ 25 and write each batch transactionally
        List<List<ReadingItem>> batches = partition(items, BATCH_SIZE);
        List<List<ReadingItem>> committedBatches = new ArrayList<>();

        for (List<ReadingItem> batch : batches) {
            try {
                TransactWriteItemsEnhancedRequest.Builder requestBuilder =
                        TransactWriteItemsEnhancedRequest.builder();
                for (ReadingItem item : batch) {
                    requestBuilder.addPutItem(table, item);
                }
                enhancedClient.transactWriteItems(requestBuilder.build());
                committedBatches.add(batch);
            } catch (TransactionCanceledException e) {
                log.error("Transaction cancelled for batch. Attempting compensating deletes. Reason: {}",
                        e.getMessage());
                compensateCommittedBatches(committedBatches);
                throw new DatabaseConstraintException("Transaction cancelled: " + e.getMessage(), e);
            } catch (DynamoDbException e) {
                if (isConnectionError(e)) {
                    log.error("Database unavailable: {}", e.getMessage());
                    compensateCommittedBatches(committedBatches);
                    throw new DatabaseUnavailableException("Database unavailable: " + e.getMessage(), e);
                }
                log.error("DynamoDB error: {}", e.getMessage());
                compensateCommittedBatches(committedBatches);
                throw new DatabaseConstraintException("DynamoDB error: " + e.getMessage(), e);
            } catch (SdkClientException e) {
                log.error("Database connection failed: {}", e.getMessage());
                compensateCommittedBatches(committedBatches);
                throw new DatabaseUnavailableException("DynamoDB connection failed: " + e.getMessage(), e);
            }
        }

        return new InsertResult(requestIds);
    }

    /**
     * Attempts compensating deletes for all previously committed batches (best-effort).
     */
    private void compensateCommittedBatches(List<List<ReadingItem>> committedBatches) {
        for (List<ReadingItem> batch : committedBatches) {
            try {
                TransactWriteItemsEnhancedRequest.Builder requestBuilder =
                        TransactWriteItemsEnhancedRequest.builder();
                for (ReadingItem item : batch) {
                    ReadingItem key = new ReadingItem();
                    key.setLocation(item.getLocation());
                    key.setTimestamp(item.getTimestamp());
                    requestBuilder.addDeleteItem(table, key);
                }
                enhancedClient.transactWriteItems(requestBuilder.build());
            } catch (Exception e) {
                log.error("Compensating delete failed for batch: {}", e.getMessage());
            }
        }
    }

    /**
     * Partitions a list into sublists of at most the given size.
     */
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    /**
     * Determines if a DynamoDbException is a connection/network error.
     */
    private boolean isConnectionError(DynamoDbException e) {
        Throwable cause = e.getCause();
        if (cause instanceof java.net.ConnectException || cause instanceof java.net.UnknownHostException) {
            return true;
        }
        String message = e.getMessage();
        return message != null && (message.contains("Unable to execute") || message.contains("connection"));
    }
}
