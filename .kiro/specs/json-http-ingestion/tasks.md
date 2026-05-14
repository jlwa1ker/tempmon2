# Implementation Plan: json-http-ingestion

## Overview

Implement the hygrometer data service as a Spring Boot application with DynamoDB persistence, a REST API for ingestion and querying, and a React/Recharts frontend served as static files. The implementation proceeds from project scaffolding through data models, core service components, controllers, error handling, configuration, and finally the frontend build pipeline.

## Tasks

- [x] 1. Scaffold project structure and core configuration
  - Create Maven `pom.xml` with Spring Boot parent, Spring Web MVC, AWS SDK v2 DynamoDB, Jackson, jqwik, JUnit 5, and Mockito dependencies
  - Create `src/main/resources/application.properties` mapping all env vars (`DATABASE_URL`, `PORT`, `MAX_PAYLOAD_BYTES`, `REQUEST_TIMEOUT_SECONDS`, `INGEST_PATH`, `READINGS_PATH`) via `${ENV_VAR:default}` syntax
  - Create `HygrometerApplication` main class with `@SpringBootApplication`
  - Create `AppConfig` `@Configuration` class that reads and validates all env vars at startup; logs a descriptive error and calls `System.exit(1)` for missing required vars (`DATABASE_URL`, `PORT`) or out-of-range values; produces `DynamoDbClient`, `DynamoDbEnhancedClient`, and `DynamoDbTable<ReadingItem>` beans
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_

- [x] 2. Implement data models
  - [x] 2.1 Create `ReadingItem` DynamoDB bean class
    - Annotate with `@DynamoDbBean`, `@DynamoDbPartitionKey` on `location`, `@DynamoDbSortKey` on `timestamp`
    - Include `requestId`, `temperatureF` (BigDecimal), `humidityPct` (BigDecimal), `ingestedAt` fields
    - _Requirements: 4.1, 4.3, 4.4_

  - [x] 2.2 Create `HygrometerReading` Java record
    - Fields: `OffsetDateTime timestamp`, `BigDecimal temperatureF`, `BigDecimal humidityPct`, `String location`
    - _Requirements: 2.1, 3.1_

  - [x] 2.3 Create `SkippedReading`, `FilterResult`, `InsertResult`, `Payload`, `ValidationResult`, and `ValidationFailure` records/classes
    - `SkippedReading(int index, String reason)` — reason is `"exact_match"` or `"conflicting_data"`
    - `FilterResult(List<HygrometerReading> readingsToInsert, List<SkippedReading> skipped)`
    - `InsertResult(List<String> requestIds)`
    - `Payload` wrapping the parsed `readings` list
    - `ValidationResult` with `valid` flag, list of `HygrometerReading`, and list of `ValidationFailure`
    - `ValidationFailure(int index, String field, String message)`
    - _Requirements: 3.2, 4.3, 5.2, 5.3, 10.3, 10.4_

  - [x] 2.4 Create custom exception classes
    - `DatabaseUnavailableException` and `DatabaseConstraintException` (both unchecked)
    - `ParseException` (unchecked, carries HTTP status and error code)
    - _Requirements: 4.6, 4.7, 6.1_

- [x] 3. Implement `PayloadParser` service
  - [x] 3.1 Implement `PayloadParser` Spring `@Service`
    - Reject empty body → throw `ParseException` (400, `EMPTY_BODY`)
    - Parse JSON with Jackson `ObjectMapper`; catch `JsonProcessingException` → throw `ParseException` (400, `MALFORMED_JSON`) with parse error detail
    - Assert top-level value is an object → throw `ParseException` (400, `INVALID_PAYLOAD_STRUCTURE`)
    - Assert `readings` key exists and is an array → throw `ParseException` (400, `INVALID_PAYLOAD_STRUCTURE`)
    - Assert `readings` array is non-empty → throw `ParseException` (400, `EMPTY_READINGS`)
    - Return `Payload` with the parsed readings array
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

  - [x]* 3.2 Write property test for parsing round-trip fidelity
    - **Property 1: Parsing round-trip fidelity**
    - Generator: arbitrary valid JSON objects with a `readings` array of 1–50 items with valid field values
    - Action: `parse(body)` → `serialize(payload)` → `parse(serialized)`
    - Assertion: second parse result equals first parse result (same keys, values, types)
    - **Validates: Requirements 2.8**

  - [x]* 3.3 Write unit tests for `PayloadParser`
    - Empty body → 400 `EMPTY_BODY`
    - Malformed JSON (various forms) → 400 `MALFORMED_JSON` with descriptive message
    - Top-level array/string/null → 400 `INVALID_PAYLOAD_STRUCTURE`
    - Missing `readings` key → 400 `INVALID_PAYLOAD_STRUCTURE`
    - `readings` not an array → 400 `INVALID_PAYLOAD_STRUCTURE`
    - Empty `readings` array → 400 `EMPTY_READINGS`
    - Valid payload → `Payload` with correct structure
    - _Requirements: 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

- [x] 4. Implement `ReadingValidator` service
  - [x] 4.1 Implement `ReadingValidator` Spring `@Service`
    - Validate each item in the `readings` array: must be a JSON object; `timestamp` present, string, RFC 3339 parseable via `OffsetDateTime.parse()`; `temperature_f` present, numeric, in [-100, 200]; `humidity_pct` present, numeric, in [0, 100]; `location` present, non-empty string, 1–255 characters
    - Collect all failures before returning (no fail-fast)
    - Return `ValidationResult.valid(readings)` or `ValidationResult.invalid(failures)`
    - _Requirements: 3.1, 3.2, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10_

  - [x]* 4.2 Write property test for whitespace-only and empty location rejection
    - **Property 2: Whitespace-only and empty location rejection**
    - Generator: arbitrary `HygrometerReading` with `location` drawn from strings of only whitespace characters (space, tab, newline) of length 0–255
    - Assertion: result is invalid; failure list contains exactly one entry for `location`
    - **Validates: Requirements 3.10**

  - [x]* 4.3 Write property test for out-of-range numeric field rejection
    - **Property 3: Out-of-range numeric field rejection**
    - Generator: arbitrary readings with `temperature_f` outside [-100, 200] OR `humidity_pct` outside [0, 100]
    - Assertion: result is invalid; failure list contains an entry for the out-of-range field with the received value and valid range
    - **Validates: Requirements 3.7, 3.9**

  - [x]* 4.4 Write property test for validation failure completeness
    - **Property 8: Validation failure completeness**
    - Generator: arbitrary payloads where a random subset of readings have one or more invalid fields
    - Assertion: failure list contains exactly one entry per failing reading; each entry's index matches the reading's position in the input list; no failing reading is omitted
    - **Validates: Requirements 3.2**

  - [x]* 4.5 Write unit tests for `ReadingValidator`
    - All-valid batch → `ValidationResult.valid()`
    - Missing each required field → failure recorded with correct index and field name
    - `temperature_f` at boundary values (-100, 200, -100.1, 200.1) → pass/fail
    - `humidity_pct` at boundary values (0, 100, -0.1, 100.1) → pass/fail
    - `location` at length boundaries (1 char, 255 chars, 256 chars, empty, whitespace-only)
    - Multiple failures in one payload → all failures present in result
    - Non-object reading item → failure recorded
    - _Requirements: 3.1, 3.2, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10_

- [x] 5. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement `DuplicateFilter` service
  - [x] 6.1 Implement `DuplicateFilter` Spring `@Service`
    - Scan input list for intra-batch duplicates (same `timestamp` + `location`); log WARNING for each duplicate after the first; keep first occurrence as candidate; add subsequent occurrences to skipped list with reason `"conflicting_data"`
    - Use `DynamoDbEnhancedClient.batchGetItem()` to fetch existing records for all candidate `(location, timestamp)` pairs; issue multiple round-trips if there are more than 100 candidate keys
    - Exact-match inter-batch duplicate (all field values identical) → silent skip, reason `"exact_match"`
    - Conflicting inter-batch duplicate (any value differs) → skip and log WARNING, reason `"conflicting_data"`
    - Return `FilterResult(readingsToInsert, skipped)`
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

  - [x]* 6.2 Write property test for duplicate filter completeness
    - **Property 9: Duplicate filter completeness**
    - Generator: arbitrary lists of `HygrometerReading` objects where a random subset share `timestamp` + `location` with pre-seeded "existing" records returned by a mocked `batchGetItem`
    - Assertion: `FilterResult.readingsToInsert` contains no reading whose `(timestamp, location)` matches any existing record; `FilterResult.skipped` contains exactly one entry for every duplicate reading; the union of `readingsToInsert` indices and `skipped` indices covers all N input readings
    - **Validates: Requirements 10.3, 10.4, 10.5**

  - [x]* 6.3 Write unit tests for `DuplicateFilter`
    - No duplicates in batch, no existing DB records → all readings in `readingsToInsert`, empty `skipped` list
    - Intra-batch duplicate with identical values → WARNING logged, second occurrence in `skipped` with reason `"conflicting_data"`
    - Intra-batch duplicate with different values → WARNING logged, second occurrence in `skipped` with reason `"conflicting_data"`
    - Inter-batch duplicate (exact match in DB) → silent skip, entry in `skipped` with reason `"exact_match"`
    - Inter-batch duplicate (conflicting data in DB) → WARNING logged, entry in `skipped` with reason `"conflicting_data"`
    - Mix of new, exact-match, and conflicting duplicates → correct partition
    - More than 100 candidate keys → `batchGetItem` called in multiple round-trips of ≤ 100 keys each
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

- [x] 7. Implement `StorageService`
  - [x] 7.1 Implement `StorageService` Spring `@Service`
    - Accept list of non-duplicate `HygrometerReading` objects (duplicate filtering already applied)
    - Assign UUID v4 `request_id` and UTC ingestion timestamp (`ingestedAt`) to each reading
    - Split into batches of ≤ 25 and call `transactWriteItems` for each batch
    - On failure of any batch, attempt compensating deletes for already-committed batches (best-effort; log any compensation failure at ERROR level)
    - Return `InsertResult` containing ordered list of `request_id` values
    - Throw `DatabaseUnavailableException` on connection errors; throw `DatabaseConstraintException` on `TransactionCanceledException` or other `SdkException`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.6, 4.7_

  - [x]* 7.2 Write property test for all-or-nothing persistence
    - **Property 4: All-or-nothing persistence**
    - Uses DynamoDB Local / LocalStack
    - Generator: arbitrary valid reading lists of 1–50 items
    - Action (success path): call `storageService.persist(readings)`; query DynamoDB for all inserted records; assert exactly N records present with matching field values
    - Action (failure path): inject a fault on the second batch; call `persist`; assert zero records from that payload remain in the table
    - **Validates: Requirements 4.2**

  - [x]* 7.3 Write property test for request ID uniqueness and ordering
    - **Property 5: Request ID uniqueness and ordering**
    - Generator: arbitrary valid reading lists of 1–100 items (mocked DynamoDB)
    - Assertion: result contains exactly N UUIDs; all are distinct; order matches input order
    - **Validates: Requirements 4.3, 5.2**

  - [x]* 7.4 Write unit tests for `StorageService`
    - Single batch (≤ 25 items) → `transactWriteItems` called once; result contains N UUIDs
    - Two batches (26 items) → `transactWriteItems` called twice
    - First batch succeeds, second fails → compensating deletes attempted; `DatabaseConstraintException` thrown
    - DynamoDB connection error → `DatabaseUnavailableException` thrown
    - _Requirements: 4.1, 4.2, 4.3, 4.6, 4.7_

- [x] 8. Implement `QueryHandler` service
  - [x] 8.1 Implement `QueryHandler` Spring `@Service`
    - Accept `start` (OffsetDateTime), `end` (OffsetDateTime), and `locations` (List<String>)
    - Issue one DynamoDB `Query` per location (PK = location, SK BETWEEN ISO 8601 start and end strings, inclusive)
    - Merge results from all locations, sort by timestamp ascending
    - Cap at 10,000 records; set `truncated = true` if raw result count exceeded the cap
    - Throw `DatabaseUnavailableException` on connection errors
    - _Requirements: 8.7, 8.8, 8.9, 8.10_

  - [x]* 8.2 Write property test for query result ordering and bounds
    - **Property 6: Query result ordering and bounds**
    - Generator: arbitrary sets of `ReadingItem` records pre-loaded into DynamoDB Local; arbitrary `start`, `end`, `locations` parameters
    - Assertion: every returned record has `timestamp` in [start, end] and `location` in the requested set; records are sorted ascending by timestamp; count ≤ 10,000; `truncated` is correct
    - **Validates: Requirements 8.7, 8.8**

  - [x]* 8.3 Write unit tests for `QueryHandler`
    - Single location, results within range → returned sorted
    - Multiple locations → results merged and sorted
    - Result count = 10,000 → `truncated = false`
    - Result count = 10,001 → `truncated = true`, 10,000 items returned
    - Empty result → empty list, `truncated = false`
    - _Requirements: 8.7, 8.8, 8.9_

- [x] 9. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Implement `RequestIdFilter` and `GlobalExceptionHandler`
  - [x] 10.1 Implement `RequestIdFilter` as a Spring `@Component` servlet filter
    - Generate UUID v4 per request; store in MDC and a request-scoped holder
    - Attach UUID as `X-Request-ID` response header before the response is committed
    - Exclude the health-check path from `X-Request-ID` attachment on successful responses (failed health-check responses still get the header per Requirement 5.6)
    - _Requirements: 5.6_

  - [x] 10.2 Implement `GlobalExceptionHandler` as `@RestControllerAdvice`
    - `AsyncRequestTimeoutException` → 408 with `REQUEST_TIMEOUT` error code
    - `HttpMediaTypeNotSupportedException` → 415 with `UNSUPPORTED_MEDIA_TYPE`
    - `HttpRequestMethodNotSupportedException` → 405 with `METHOD_NOT_ALLOWED`
    - `ParseException` → mapped HTTP status and error code from exception
    - `DatabaseUnavailableException` → 503 with `DATABASE_UNAVAILABLE`
    - `DatabaseConstraintException` → 500 with `DATABASE_ERROR`
    - All other `Throwable` → 500 with `INTERNAL_ERROR`; sanitize response body (no stack trace, no class names, no file paths); log full stack trace + `X-Request-ID` at ERROR level
    - Per Requirement 6.6: if both timeout and unhandled error occur, return 500
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [x]* 10.3 Write property test for error response never exposing internals
    - **Property 7: Error response never exposes internals**
    - Generator: arbitrary `RuntimeException` instances with arbitrary messages (including class names, file paths, stack trace fragments)
    - Action: invoke `GlobalExceptionHandler.handleUnexpected(exception, webRequest)`
    - Assertion: response body JSON does not contain any Java class name pattern (`[A-Z][a-zA-Z]+Exception`, `at com\.`, file path separators); response conforms to the error response schema
    - **Validates: Requirements 6.1**

  - [x]* 10.4 Write unit tests for `GlobalExceptionHandler`
    - `AsyncRequestTimeoutException` → 408 with `REQUEST_TIMEOUT` error code
    - `HttpMediaTypeNotSupportedException` → 415
    - `HttpRequestMethodNotSupportedException` → 405
    - `ParseException` → correct status and error code
    - `DatabaseUnavailableException` → 503
    - `DatabaseConstraintException` → 500
    - Arbitrary `RuntimeException` → 500 with no stack trace in body
    - _Requirements: 6.1, 6.2, 6.3_

- [x] 11. Implement `PayloadSizeFilter`
  - Create a `OncePerRequestFilter` that enforces `MAX_PAYLOAD_BYTES` before the body reaches the controller
  - If the body exceeds the limit, return 422 with `PAYLOAD_TOO_LARGE` immediately
  - A payload whose size equals `MAX_PAYLOAD_BYTES` exactly is accepted
  - _Requirements: 3.3_

- [x] 12. Implement `IngestController`
  - [x] 12.1 Implement `IngestController` `@RestController` mapped to `POST ${app.ingest-path}`
    - Return `Callable<ResponseEntity<?>>` for async timeout support
    - Enforce `Content-Type: application/json`; delegate to `PayloadParser`, `ReadingValidator`, `DuplicateFilter`, `StorageService` in sequence
    - Build success response: `status`, `inserted_count`, `skipped_count`, `request_ids`, and optional `skipped` array
    - Return 201 if `inserted_count > 0`; return 200 if `inserted_count == 0`
    - Catch `DatabaseUnavailableException` → 503; `DatabaseConstraintException` → 500; all other exceptions handled by `GlobalExceptionHandler`
    - _Requirements: 1.1, 1.2, 1.4, 1.5, 4.5, 4.6, 4.7, 5.1, 5.2, 5.3, 5.4, 5.5, 10.6, 10.7, 10.8_

  - [x]* 12.2 Write property test for inserted_count + skipped_count invariant
    - **Property 10: inserted_count + skipped_count invariant**
    - Generator: arbitrary valid payloads of N readings (1–50); `DuplicateFilter` mock returns a random partition into `readingsToInsert` (size K) and `skipped` (size N−K); `StorageService` mock returns K UUIDs
    - Action: full `IngestController` invocation
    - Assertion: response body `inserted_count + skipped_count == N`
    - **Validates: Requirements 10.8**

- [x] 13. Implement `ReadingsController` and `HealthController`
  - [x] 13.1 Implement `ReadingsController` `@RestController` mapped to `GET ${app.readings-path}`
    - Return `Callable<ResponseEntity<?>>` for timeout support
    - Validate `start`, `end` (RFC 3339, `end` strictly after `start`), and `locations` (one or more non-empty strings, 1–255 chars each) query parameters; return 400 with `INVALID_QUERY_PARAMS` for any violation
    - Delegate to `QueryHandler`; return 200 with `readings` array (and `truncated` field when applicable)
    - Return 405 for non-GET methods at this path
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8, 8.9, 8.10, 8.11_

  - [x] 13.2 Implement `HealthController` `@RestController` mapped to `GET /health`
    - Return 200 OK with `{"status":"ok"}`; no `X-Request-ID` header on successful responses
    - _Requirements: 1.3_

- [x] 14. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 15. Write integration tests
  - [x]* 15.1 Write Spring Boot integration tests using DynamoDB Local
    - Full POST `/ingest` round-trip: valid payload → 201, records queryable via GET `/readings`
    - POST `/ingest` with invalid payload → 422, no records written
    - GET `/readings` with valid params → 200, correct records returned
    - GET `/readings` with missing params → 400
    - Database unavailable (stop DynamoDB Local) → 503
    - Wrong `Content-Type` → 415
    - Wrong HTTP method → 405
    - Payload exceeding `MAX_PAYLOAD_BYTES` → 422
    - Duplicate readings in payload → correct `inserted_count` and `skipped_count`
    - _Requirements: 1.1, 1.2, 1.4, 1.5, 3.3, 4.1, 4.2, 4.5, 8.2, 8.3, 10.6, 10.7, 10.8_

- [x] 16. Build the React frontend
  - [x] 16.1 Scaffold the Vite + React project under `frontend/`
    - Run `npm create vite@latest frontend -- --template react`; install `recharts` dependency
    - Configure Vite to proxy `/readings` to the Spring Boot backend during development
    - _Requirements: 9.1_

  - [x] 16.2 Implement the query form component
    - Start datetime input and end datetime input (RFC 3339 format)
    - Location input control supporting one or more location names (each 1–255 characters)
    - Metric selector: temperature (°F) or humidity (%)
    - Submit button; client-side validation before sending: RFC 3339 format check, non-empty location list; display validation messages identifying the invalid field without sending a request
    - _Requirements: 9.2, 9.3, 9.4, 9.9_

  - [x] 16.3 Implement the query submission and loading state
    - On submit, send GET to `/readings` with `start`, `end`, and repeated `locations` query parameters
    - Display loading indicator and disable submit button while request is in progress
    - Re-enable submit and remove loading indicator when request completes (success or error)
    - _Requirements: 9.5, 9.10, 9.11_

  - [x] 16.4 Implement the Recharts line chart and result display
    - Render a line chart when the response contains a non-empty `readings` array: x-axis = time, y-axis = selected metric labeled with name and unit (°F or %), each distinct location as a separate labeled series
    - Display "no data found" message when `readings` array is empty
    - Display human-readable error message (HTTP status code + `message` field if present, otherwise generic description) when the endpoint returns an HTTP error
    - _Requirements: 9.6, 9.7, 9.8_

  - [ ]* 16.5 Write frontend unit tests with Vitest + React Testing Library
    - Form validation logic: RFC 3339 format check, empty location list check
    - Snapshot tests for chart rendering with mock data
    - _Requirements: 9.9_

- [x] 17. Wire frontend into Spring Boot static serving
  - Configure Maven build to run `npm run build` in `frontend/` and copy the `dist/` output to `src/main/resources/static/`
  - Verify `HealthController` or a `WebMvcConfigurer` serves `index.html` at the root path `/`
  - _Requirements: 9.1_

- [x] 18. Add Dockerfile and GitHub Actions CI/CD workflow
  - [x] 18.1 Create `Dockerfile` at the project root
    - Multi-stage build: build React frontend, copy `dist/` to Spring Boot static resources, run `mvn package`, produce a minimal JRE runtime image
    - Expose the configured `PORT`
    - _Requirements: 7.3_

  - [x] 18.2 Create GitHub Actions workflow at `.github/workflows/ci.yml`
    - Trigger on push to `main` and on pull requests targeting `main`; deploy only on push to `main`
    - Steps: build React frontend (`npm run build` in `frontend/`), copy `dist/` to `src/main/resources/static/`, run `mvn verify`, build Docker image, push to Amazon ECR, update ECS task definition and deploy to Fargate
    - Use `ubuntu-latest` runner
    - _Requirements: 7.1, 7.2_

- [x] 19. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at key milestones
- Property tests (jqwik) validate universal correctness properties; unit tests validate specific examples and edge cases
- DynamoDB Local or LocalStack is required for property tests 4 and 6 and all integration tests
- The `temperature_f` valid range in the requirements is [-100, 200] (Requirements 3.7); the design document mentions [-999, 999] in the validator description — the requirements document takes precedence

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["2.1", "2.2", "2.3", "2.4"] },
    { "id": 1, "tasks": ["3.1", "4.1", "7.1", "8.1"] },
    { "id": 2, "tasks": ["3.2", "3.3", "4.2", "4.3", "4.4", "4.5", "6.1", "7.2", "7.3", "7.4", "8.2", "8.3"] },
    { "id": 3, "tasks": ["6.2", "6.3", "10.1", "10.2", "11"] },
    { "id": 4, "tasks": ["10.3", "10.4", "12.1", "13.1", "13.2"] },
    { "id": 5, "tasks": ["12.2", "15.1"] },
    { "id": 6, "tasks": ["16.1"] },
    { "id": 7, "tasks": ["16.2", "16.3"] },
    { "id": 8, "tasks": ["16.4", "16.5"] },
    { "id": 9, "tasks": ["17"] },
    { "id": 10, "tasks": ["18.1", "18.2"] }
  ]
}
```
