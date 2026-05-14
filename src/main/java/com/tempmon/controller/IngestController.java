package com.tempmon.controller;

import com.tempmon.exception.DatabaseConstraintException;
import com.tempmon.exception.DatabaseUnavailableException;
import com.tempmon.model.FilterResult;
import com.tempmon.model.HygrometerReading;
import com.tempmon.model.InsertResult;
import com.tempmon.model.SkippedReading;
import com.tempmon.model.ValidationResult;
import com.tempmon.service.DuplicateFilter;
import com.tempmon.service.PayloadParser;
import com.tempmon.service.ReadingValidator;
import com.tempmon.service.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * REST controller for the hygrometer data ingestion endpoint.
 *
 * <p>Accepts batched JSON payloads of temperature and humidity readings via
 * HTTP POST, validates, deduplicates, and persists them to DynamoDB.
 *
 * <p>Returns {@code Callable<ResponseEntity<?>>} so Spring MVC's async timeout
 * ({@code spring.mvc.async.request-timeout}) applies automatically.
 *
 * <p>Requirements: 1.1, 1.2, 1.4, 1.5, 4.5, 4.6, 4.7, 5.1, 5.2, 5.3, 5.4, 5.5
 */
@RestController
public class IngestController {

    private final PayloadParser payloadParser;
    private final ReadingValidator readingValidator;
    private final DuplicateFilter duplicateFilter;
    private final StorageService storageService;

    public IngestController(PayloadParser payloadParser,
                            ReadingValidator readingValidator,
                            DuplicateFilter duplicateFilter,
                            StorageService storageService) {
        this.payloadParser = payloadParser;
        this.readingValidator = readingValidator;
        this.duplicateFilter = duplicateFilter;
        this.storageService = storageService;
    }

    /**
     * Ingests a batch of hygrometer readings.
     *
     * @param rawBody the raw JSON request body (may be null if empty)
     * @param request the HTTP servlet request
     * @return a callable that produces the response entity
     */
    @PostMapping(
            value = "${app.ingest-path:/ingest}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Callable<ResponseEntity<?>> ingest(@RequestBody(required = false) String rawBody,
                                              HttpServletRequest request) {
        return () -> {
            // Step 1: Parse the payload
            var payload = payloadParser.parse(rawBody);

            // Step 2: Validate all readings
            ValidationResult validationResult = readingValidator.validate(payload);
            if (!validationResult.valid()) {
                return buildValidationErrorResponse(validationResult);
            }

            // Step 3: Filter duplicates
            List<HygrometerReading> validReadings = validationResult.readings();
            FilterResult filterResult = duplicateFilter.filter(validReadings);

            // Step 4: Persist non-duplicate readings
            List<HygrometerReading> readingsToInsert = filterResult.readingsToInsert();
            InsertResult insertResult;
            try {
                insertResult = storageService.persist(readingsToInsert);
            } catch (DatabaseUnavailableException e) {
                return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE,
                        "DATABASE_UNAVAILABLE", "Service temporarily unavailable");
            } catch (DatabaseConstraintException e) {
                return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                        "DATABASE_ERROR", "An internal error occurred");
            }

            // Step 5: Build success response
            return buildSuccessResponse(insertResult, filterResult);
        };
    }

    /**
     * Builds a 422 response for validation failures.
     */
    private ResponseEntity<?> buildValidationErrorResponse(ValidationResult validationResult) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("error", "INVALID_PAYLOAD");

        // Build a combined message from all failures
        var failures = validationResult.failures();
        if (!failures.isEmpty()) {
            body.put("message", failures.get(0).message());
        }
        body.put("failures", failures.stream()
                .map(f -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("index", f.index());
                    entry.put("field", f.field());
                    entry.put("message", f.message());
                    return entry;
                })
                .toList());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * Builds the success response (201 if records inserted, 200 if all were duplicates).
     */
    private ResponseEntity<?> buildSuccessResponse(InsertResult insertResult, FilterResult filterResult) {
        int insertedCount = insertResult.requestIds().size();
        int skippedCount = filterResult.skipped().size();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "success");
        body.put("inserted_count", insertedCount);
        body.put("skipped_count", skippedCount);
        body.put("request_ids", insertResult.requestIds());

        // Include skipped array only when there are skipped readings
        if (!filterResult.skipped().isEmpty()) {
            body.put("skipped", filterResult.skipped().stream()
                    .map(s -> {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("index", s.index());
                        entry.put("reason", s.reason());
                        return entry;
                    })
                    .toList());
        }

        HttpStatus status = insertedCount > 0 ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * Builds a standard error response.
     */
    private ResponseEntity<?> buildErrorResponse(HttpStatus status, String errorCode, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("error", errorCode);
        body.put("message", message);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
