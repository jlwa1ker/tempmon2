package com.tempmon.controller;

import com.tempmon.model.ReadingItem;
import com.tempmon.service.QueryHandler;
import com.tempmon.service.QueryHandler.QueryResult;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * REST controller for querying stored hygrometer readings by time range and location.
 *
 * <p>Validates query parameters (start, end, locations) and delegates to {@link QueryHandler}.
 * Returns 400 with INVALID_QUERY_PARAMS for any validation violation.
 *
 * <p>Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8, 8.9, 8.10, 8.11
 */
@RestController
public class ReadingsController {

    private final QueryHandler queryHandler;

    public ReadingsController(QueryHandler queryHandler) {
        this.queryHandler = queryHandler;
    }

    /**
     * Handles GET requests to the readings path.
     * Returns Callable for async timeout support via Spring MVC.
     */
    @GetMapping(
            value = "${app.readings-path:/readings}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Callable<ResponseEntity<?>> getReadings(
            @RequestParam(name = "start", required = false) String start,
            @RequestParam(name = "end", required = false) String end,
            @RequestParam(name = "locations", required = false) List<String> locations) {

        return () -> {
            // Validate start parameter
            if (start == null || start.isBlank()) {
                return buildErrorResponse("'start' query parameter is required");
            }

            // Validate end parameter
            if (end == null || end.isBlank()) {
                return buildErrorResponse("'end' query parameter is required");
            }

            // Parse start as RFC 3339
            OffsetDateTime startDateTime;
            try {
                startDateTime = OffsetDateTime.parse(start, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } catch (DateTimeParseException e) {
                return buildErrorResponse("'start' must be a valid RFC 3339 datetime (e.g., 2024-01-15T10:30:00Z)");
            }

            // Parse end as RFC 3339
            OffsetDateTime endDateTime;
            try {
                endDateTime = OffsetDateTime.parse(end, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } catch (DateTimeParseException e) {
                return buildErrorResponse("'end' must be a valid RFC 3339 datetime (e.g., 2024-01-15T10:30:00Z)");
            }

            // Validate end is strictly after start
            if (!endDateTime.isAfter(startDateTime)) {
                return buildErrorResponse("'end' must be after 'start'");
            }

            // Validate locations parameter
            if (locations == null || locations.isEmpty()) {
                return buildErrorResponse("At least one 'locations' query parameter is required");
            }

            // Filter out empty strings and validate each location
            List<String> validLocations = new ArrayList<>();
            for (String loc : locations) {
                if (loc == null || loc.isEmpty()) {
                    continue;
                }
                if (loc.length() > 255) {
                    return buildErrorResponse("Each location must be between 1 and 255 characters");
                }
                validLocations.add(loc);
            }

            if (validLocations.isEmpty()) {
                return buildErrorResponse("At least one non-empty location name is required");
            }

            // Delegate to QueryHandler
            QueryResult result = queryHandler.query(startDateTime, endDateTime, validLocations);

            // Build success response
            List<Map<String, Object>> readingsArray = new ArrayList<>();
            for (ReadingItem item : result.readings()) {
                Map<String, Object> reading = new LinkedHashMap<>();
                reading.put("timestamp", item.getTimestamp());
                reading.put("temperature_f", item.getTemperatureF());
                reading.put("humidity_pct", item.getHumidityPct());
                reading.put("location", item.getLocation());
                readingsArray.add(reading);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("readings", readingsArray);
            body.put("truncated", result.truncated());

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        };
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("error", "INVALID_QUERY_PARAMS");
        body.put("message", message);
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
