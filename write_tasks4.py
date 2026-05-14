
TASKS_FILE = r'c:\Users\walke\git\tempmon2\.kiro\specs\json-http-ingestion\tasks.md'

TASK7_8 = """
### Task 7: DuplicateFilter — Implementation, Unit Tests & Property-Based Tests

**Goal**: Implement `DuplicateFilter` to detect and remove both intra-batch and inter-batch duplicate readings before any write is attempted, and verify correctness with example-based and property-based tests.

#### Sub-tasks

- [ ] 7.1 Create `DuplicateFilter` as a Spring `@Service`. Inject `DynamoDbEnhancedClient` and `DynamoDbTable<ReadingItem>`.
- [ ] 7.2 Implement `filter(List<HygrometerReading> readings)` returning `FilterResult`:
  - **Intra-batch duplicate detection**: iterate the list in order; for each reading, check if an earlier reading in the same list shares the same `timestamp` + `location`. If so, log a WARNING and add the later reading to `skipped` with reason `"conflicting_data"`. Keep only the first occurrence as a candidate.
  - **Inter-batch duplicate detection**: collect all candidate `(location, timestamp)` pairs; split into batches of at most 100 keys; call `DynamoDbEnhancedClient.batchGetItem()` for each batch to fetch existing records.
    - Candidate matches existing record with identical `temperatureF` and `humidityPct` → add to `skipped` with reason `"exact_match"` (no log).
    - Candidate matches existing record with differing values → log WARNING; add to `skipped` with reason `"conflicting_data"`.
    - No match → keep in `readingsToInsert`.
  - Return `FilterResult(readingsToInsert, skipped)`.
- [ ] 7.3 Write `DuplicateFilterTest` (JUnit 5 + Mockito) covering:
  - No duplicates, no existing DB records → all in `readingsToInsert`, empty `skipped`.
  - Intra-batch duplicate (identical values) → WARNING logged, second in `skipped` with `"conflicting_data"`.
  - Intra-batch duplicate (different values) → WARNING logged, second in `skipped` with `"conflicting_data"`.
  - Inter-batch exact match → silent skip, `"exact_match"` in `skipped`.
  - Inter-batch conflicting match → WARNING logged, `"conflicting_data"` in `skipped`.
  - Mix of new, exact-match, and conflicting → correct partition.
  - 101 candidate keys → `batchGetItem` called twice (batches of 100 and 1).
- [ ] 7.4 Write property-based test `DuplicateFilterPropertyTest` using jqwik:
  - **Property 9** (`@Property`, tag `"Property 9: duplicate filter completeness"`): Generate arbitrary lists of `HygrometerReading` objects where a random subset share `timestamp` + `location` with pre-seeded "existing" records returned by the mocked `batchGetItem`. Assert: `readingsToInsert` contains no reading whose `(timestamp, location)` matches any existing record; `skipped` contains exactly one entry for every duplicate reading; the union of `readingsToInsert` and `skipped` covers all N input readings. **Validates: Requirements 10.3, 10.4, 10.5**

#### Acceptance Criteria

- All `DuplicateFilterTest` example tests pass.
- Property 9 passes with at least 100 iterations.
- Intra-batch duplicates always produce `"conflicting_data"` regardless of whether field values are identical (Requirement 10.2).
- Exact inter-batch duplicates produce no log entry (Requirement 10.3).
- Conflicting inter-batch duplicates produce a WARNING log entry (Requirement 10.4).
- `readingsToInsert` never contains a reading whose key matches an existing DB record (Requirement 10.5).
- `batchGetItem` is called in batches of at most 100 keys (DynamoDB API limit).

**Validates**: Requirements 10.1–10.5

---

### Task 8: StorageService — Implementation, Unit Tests & Property-Based Tests

**Goal**: Implement `StorageService` to persist non-duplicate readings atomically using `TransactWriteItems`, with compensating deletes on partial failure, and verify correctness with example-based and property-based tests.

#### Sub-tasks

- [ ] 8.1 Create `StorageService` as a Spring `@Service`. Inject `DynamoDbEnhancedClient` and `DynamoDbTable<ReadingItem>`.
- [ ] 8.2 Implement `persist(List<HygrometerReading> readings)` returning `InsertResult`:
  - Assign a UUID v4 `requestId` and a UTC `ingestedAt` timestamp (ISO 8601, ms precision) to each reading.
  - Convert each `HygrometerReading` to a `ReadingItem`, normalizing `timestamp` to UTC before storage.
  - Split the list into batches of at most 25 items.
  - For each batch, call `transactWriteItems` (put operations). Track successfully committed batches.
  - If any batch fails: attempt compensating deletes for all previously committed batches using `transactWriteItems` (delete operations); log any compensation failure at ERROR level.
  - On `DynamoDbException` with connection/network cause → throw `DatabaseUnavailableException`.
  - On `TransactionCanceledException` → throw `DatabaseConstraintException`.
  - On other `SdkException` → throw `DatabaseConstraintException` with original message.
  - On success → return `InsertResult` with the ordered list of `requestId` strings.
- [ ] 8.3 Write `StorageServiceTest` (JUnit 5 + Mockito) covering:
  - Single batch (25 items) → `transactWriteItems` called once; result has 25 UUIDs.
  - Two batches (26 items) → `transactWriteItems` called twice.
  - First batch succeeds, second throws `TransactionCanceledException` → compensating delete attempted for first batch; `DatabaseConstraintException` thrown.
  - `DynamoDbException` with connection cause → `DatabaseUnavailableException` thrown.
  - Empty list → returns `InsertResult` with empty list, no DynamoDB calls.
- [ ] 8.4 Write property-based test `StorageServicePropertyTest` using jqwik:
  - **Property 5** (`@Property`, tag `"Property 5: request ID uniqueness and ordering"`): Generate arbitrary valid reading lists of 1–100 items (mocked DynamoDB). Assert: `InsertResult` contains exactly N UUIDs; all are distinct; order matches input order. **Validates: Requirements 4.3, 5.2**

#### Acceptance Criteria

- All `StorageServiceTest` example tests pass.
- Property 5 passes with at least 100 iterations.
- Batches are at most 25 items (DynamoDB `TransactWriteItems` limit, design decision).
- Compensating deletes are attempted on partial failure (Requirement 4.2, design compensating deletes section).
- `DatabaseUnavailableException` is thrown on connection errors (Requirement 4.6).
- `DatabaseConstraintException` is thrown on constraint violations (Requirement 4.7).
- Each `requestId` in `InsertResult` is a valid UUID v4 string (Requirement 4.3).
- `ingestedAt` is stored with millisecond precision in UTC ISO 8601 format (Requirement 4.4).

**Validates**: Requirements 4.1–4.7, 5.2

---

"""

with open(TASKS_FILE, 'a', encoding='utf-8') as f:
    f.write(TASK7_8)

print("Tasks 7-8 written")
