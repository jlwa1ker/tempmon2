package com.tempmon.controller;

import com.tempmon.model.ReadingItem;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.util.*;

/**
 * REST controller for retrieving the list of unique location names
 * from the hygrometer_readings DynamoDB table.
 */
@RestController
public class LocationsController {

    private final DynamoDbTable<ReadingItem> readingsTable;

    public LocationsController(DynamoDbTable<ReadingItem> readingsTable) {
        this.readingsTable = readingsTable;
    }

    /**
     * Returns the distinct location names (partition keys) from the readings table.
     */
    @GetMapping(value = "/locations", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getLocations() {
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

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("locations", new ArrayList<>(locationSet));
        return ResponseEntity.ok(body);
    }
}
