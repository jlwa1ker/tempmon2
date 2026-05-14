
TASKS_FILE = r'c:\Users\walke\git\tempmon2\.kiro\specs\json-http-ingestion\tasks.md'

TASK10_12 = """
### Task 10: QueryHandler — Implementation, Unit Tests & Property-Based Tests

**Goal**: Implement `QueryHandler` to query DynamoDB per location, merge and sort results, enforce the 10,000-record cap, and verify correctness with example-based and property-based tests.

#### Sub-tasks

- [ ] 10.1 Create `QueryHandler` as a Spring `@Service`. Inject `DynamoDbEnhancedClient` and `DynamoDbTable<ReadingItem>`.
- [ ] 10.2 Implement `query(OffsetDateTime start, OffsetDateTime end, List<String> locations)` returning `QueryResult`:
  - Normalize `start` and `end` to UTC; format as ISO 8601 strings for the DynamoDB `KeyConditionExpression`.
  - For each location, issue a DynamoDB `Query` with `PK = location AND SK BETWEEN :start AND :end` (inclusive). Page through all results.
  - Merge results from all locations into a single list.
  - Sort the merged list by `timestamp` ascending.
  - If total count > 10,000: truncate to 10,000 records, set `truncated = true`.
  - Otherwise set `truncated = false`.
  - Map each `ReadingItem` to a `HygrometerReading`.
  - On `DynamoDbException` with connection cause → throw `DatabaseUnavailableException`.
  - Return `QueryResult(readings, truncated)`.
- [ ] 10.3 Write `QueryHandlerTest` (JUnit 5 + Mockito) covering:
  - Single location, 3 results within range → returned sorted ascending.
  - Two locations → results merged and sorted.
  - Result count exactly 10,000 → `truncated = false`, 10,000 items returned.
  - Result count 10,001 → `truncated = true`, 10,000 items returned.
  - Empty result → empty list, `truncated = false`.
  - `DynamoDbException` with connection cause → `DatabaseUnavailableException` thrown.
- [ ] 10.4 Write property-based test `QueryHandlerPropertyTest` using jqwik:
  - **Property 6** (`@Property`, tag `"Property 6: query result ordering and bounds"`): Generate arbitrary sets of `ReadingItem` records pre-loaded into a mocked DynamoDB; generate arbitrary `start`, `end`, `locations` parameters. Assert: every returned record has `timestamp` in [start, end] and `location` in the requested set; records are sorted ascending by timestamp; count <= 10,000; `truncated` is `true` iff the raw result count exceeded 10,000. **Validates: Requirements 8.7, 8.8**

#### Acceptance Criteria

- All `QueryHandlerTest` example tests pass.
- Property 6 passes with at least 100 iterations.
- Results are always sorted by `timestamp` ascending (Requirement 8.8).
- Results are capped at 10,000; `truncated` is set correctly (Requirement 8.8).
- Only records with `timestamp` in [start, end] and `location` in the requested set are returned (Requirement 8.7).
- `DatabaseUnavailableException` is thrown on connection errors (Requirement 8.10).

**Validates**: Requirements 8.7, 8.8, 8.10

---

### Task 11: ReadingsController — Implementation & Unit Tests

**Goal**: Implement `ReadingsController` to validate query parameters and delegate to `QueryHandler`, returning the correct HTTP responses for all valid and invalid inputs.

#### Sub-tasks

- [ ] 11.1 Create `ReadingsController` as a `@RestController` mapped to `GET ${app.readings-path:/readings}` producing `application/json`.
- [ ] 11.2 Method signature: `public Callable<ResponseEntity<?>> getReadings(@RequestParam(required = false) String start, @RequestParam(required = false) String end, @RequestParam(required = false) List<String> locations, HttpServletRequest request)`.
- [ ] 11.3 Validate parameters inside the `Callable`:
  - `start` absent → 400, `INVALID_QUERY_PARAMS`, name the missing parameter.
  - `end` absent → 400, `INVALID_QUERY_PARAMS`, name the missing parameter.
  - `start` not parseable as `OffsetDateTime` → 400, `INVALID_QUERY_PARAMS`, name the parameter and expected format.
  - `end` not parseable as `OffsetDateTime` → 400, `INVALID_QUERY_PARAMS`.
  - `end` not strictly after `start` → 400, `INVALID_QUERY_PARAMS`, message states `end` must be after `start`.
  - `locations` absent or all values empty → 400, `INVALID_QUERY_PARAMS`, message states at least one location is required.
  - Filter out empty-string location values; reject if none remain.
  - Reject location values longer than 255 characters.
- [ ] 11.4 On valid parameters, call `QueryHandler.query(start, end, locations)`.
  - Return 200 with `{"readings": [...], "truncated": false/true}`.
  - Each element in `readings` contains `timestamp`, `temperature_f`, `humidity_pct`, `location`.
  - Catch `DatabaseUnavailableException` → 503 with `DATABASE_UNAVAILABLE`.
- [ ] 11.5 Write `ReadingsControllerTest` (JUnit 5 + Mockito, `@WebMvcTest`) covering:
  - Valid params → 200 with correct readings array.
  - Missing `start` → 400 naming `start`.
  - Missing `end` → 400 naming `end`.
  - Invalid `start` format → 400.
  - `end` not after `start` → 400.
  - Missing `locations` → 400.
  - Empty `locations` list → 400.
  - `DatabaseUnavailableException` → 503.
  - Wrong HTTP method (POST /readings) → 405.
  - Empty result → 200 with empty `readings` array (Requirement 8.9).
  - `truncated: true` when `QueryHandler` returns truncated result.

#### Acceptance Criteria

- All `ReadingsControllerTest` tests pass.
- All query parameter validation errors return 400 with `INVALID_QUERY_PARAMS` and a descriptive message (Requirements 8.3–8.6).
- 200 is returned for valid queries, including empty results (Requirements 8.2, 8.9).
- 503 is returned when the database is unavailable (Requirement 8.10).
- 405 is returned for non-GET methods (Requirement 8.11).

**Validates**: Requirements 8.1–8.11

---

### Task 12: HealthController — Implementation & Unit Tests

**Goal**: Implement the health-check endpoint that returns `{"status":"ok"}` with no `X-Request-ID` header on success.

#### Sub-tasks

- [ ] 12.1 Create `HealthController` as a `@RestController` mapped to `GET /health`.
- [ ] 12.2 Return `ResponseEntity.ok(Map.of("status", "ok"))` with `Content-Type: application/json`.
- [ ] 12.3 Confirm that `RequestIdFilter` does NOT attach `X-Request-ID` to a successful 200 response from `/health` (Requirement 5.6). Failed health-check responses (non-200) still get the header.
- [ ] 12.4 Write `HealthControllerTest` (JUnit 5, `@WebMvcTest`) covering:
  - `GET /health` → 200, body `{"status":"ok"}`, no `X-Request-ID` header.
  - `POST /health` → 405 (method not allowed), `X-Request-ID` header present.

#### Acceptance Criteria

- `GET /health` returns 200 with `{"status":"ok"}` (Requirement 1.3).
- No `X-Request-ID` header on successful health-check response (Requirement 5.6).
- `HealthControllerTest` passes.

**Validates**: Requirements 1.3, 5.6

---

"""

with open(TASKS_FILE, 'a', encoding='utf-8') as f:
    f.write(TASK10_12)

print("Tasks 10-12 written")
