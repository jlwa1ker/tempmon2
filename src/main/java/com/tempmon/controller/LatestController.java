package com.tempmon.controller;

import com.tempmon.model.ReadingItem;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.util.*;

/**
 * REST controller that returns the most recent reading for each location.
 */
@RestController
public class LatestController {

    private final DynamoDbTable<ReadingItem> readingsTable;

    public LatestController(DynamoDbTable<ReadingItem> readingsTable) {
        this.readingsTable = readingsTable;
    }

    /**
     * Returns the latest reading for each known location.
     * For each location (partition key), queries with ScanIndexForward=false and Limit=1
     * to get the newest reading by timestamp (sort key).
     */
    @GetMapping(value = "/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getLatest() {
        // First, get all distinct locations
        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .attributesToProject("location")
                .build();

        Set<String> locationSet = new TreeSet<>();
        readingsTable.scan(scanRequest)
                .items()
                .forEach(item -> {
                    if (item.getLocation() != null) {
                        locationSet.add(item.getLocation());
                    }
                });

        // For each location, query the most recent reading
        List<Map<String, Object>> readings = new ArrayList<>();
        for (String location : locationSet) {
            QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                    .queryConditional(QueryConditional.keyEqualTo(
                            Key.builder().partitionValue(location).build()
                    ))
                    .scanIndexForward(false)
                    .limit(1)
                    .build();

            readingsTable.query(queryRequest)
                    .items()
                    .stream()
                    .findFirst()
                    .ifPresent(item -> {
                        Map<String, Object> reading = new LinkedHashMap<>();
                        reading.put("location", item.getLocation());
                        reading.put("temperature_f", item.getTemperatureF());
                        reading.put("humidity_pct", item.getHumidityPct());
                        reading.put("timestamp", item.getTimestamp());
                        readings.add(reading);
                    });
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("readings", readings);
        return ResponseEntity.ok(body);
    }
}
