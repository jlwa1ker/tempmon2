
TASKS_FILE = r'c:\Users\walke\git\tempmon2\.kiro\specs\json-http-ingestion\tasks.md'

TASK9 = """
### Task 9: IngestController, RequestIdFilter, GlobalExceptionHandler — Implementation, Unit/Integration Tests & Property-Based Tests

**Goal**: Wire the ingestion pipeline end-to-end: `RequestIdFilter` attaches `X-Request-ID` to every response; `IngestController` orchestrates `PayloadParser` → `ReadingValidator` → `DuplicateFilter` → `StorageService`; `GlobalExceptionHandler` maps all exception types to the correct HTTP status and sanitized error body.

#### Sub-tasks

- [ ] 9.1 Implement `RequestIdFilter` as a Spring `@Component` implementing `javax.servlet.Filter` (or `jakarta.servlet.Filter` for Jakarta EE):
  - Generate a UUID v4 per request; store in `MDC` key `requestId` and in a `ThreadLocal` holder.
  - Attach `X-Request-ID` header to the response before it is committed.
  - Skip `X-Request-ID` attachment for successful health-check responses (path `/health`, status 200) per Requirement 5.6 (failed health-check responses still get the header).
- [ ] 9.2 Implement `IngestController` as a `@RestController` mapped to `POST ${app.ingest-path:/ingest}` consuming and producing `application/json`:
  - Method signature: `public Callable<ResponseEntity<?>> ingest(@RequestBody(required = false) String rawBody, HttpServletRequest request)`.
  - Inside the `Callable`: call `PayloadParser.parse()` → catch `ParseException` → return 400 error response.
  - Call `ReadingValidator.validate()` → if `Invalid` → return 422 error response listing all failures.
  - Call `DuplicateFilter.filter()`.
  - Call `StorageService.persist(filterResult.readingsToInsert())`.
  - Build success response: `inserted_count`, `skipped_count`, `request_ids`, and `skipped` array (omit `skipped` when empty).
  - Return 201 if `inserted_count > 0`; return 200 if `inserted_count == 0`.
  - Catch `DatabaseUnavailableException` → 503 with `DATABASE_UNAVAILABLE`.
  - Catch `DatabaseConstraintException` → 500 with `DATABASE_ERROR`.
- [ ] 9.3 Implement `GlobalExceptionHandler` as a `@RestControllerAdvice`:
  - `AsyncRequestTimeoutException` → 408, `REQUEST_TIMEOUT`.
  - `HttpMediaTypeNotSupportedException` → 415, `UNSUPPORTED_MEDIA_TYPE`.
  - `HttpRequestMethodNotSupportedException` → 405, `METHOD_NOT_ALLOWED`.
  - `MethodArgumentNotValidException` → 422, `INVALID_PAYLOAD`.
  - All other `Throwable` → 500, `INTERNAL_ERROR`, sanitized message (no class names, no stack trace, no file paths). Log full stack trace + `X-Request-ID` at ERROR level.
  - If both a timeout and an unhandled exception occur, the 500 takes precedence (Requirement 6.6).
- [ ] 9.4 Write `IngestControllerTest` (JUnit 5 + Mockito, `@WebMvcTest`) covering:
  - Valid payload → 201 with correct `inserted_count`, `request_ids`, `skipped_count`.
  - All duplicates → 200 with `inserted_count=0`.
  - `ParseException(EMPTY_BODY)` → 400.
  - `ParseException(MALFORMED_JSON)` → 400 with parse detail.
  - `ParseException(INVALID_PAYLOAD_STRUCTURE)` → 400.
  - `ParseException(EMPTY_READINGS)` → 400.
  - `ValidationResult.invalid(...)` → 422 with all failure entries.
  - `DatabaseUnavailableException` → 503.
  - `DatabaseConstraintException` → 500.
  - Wrong HTTP method (GET /ingest) → 405.
  - Wrong `Content-Type` → 415.
  - `AsyncRequestTimeoutException` → 408.
  - Unhandled `RuntimeException` → 500 with no stack trace in body.
- [ ] 9.5 Write `GlobalExceptionHandlerTest` (JUnit 5) covering:
  - `AsyncRequestTimeoutException` → 408 with `REQUEST_TIMEOUT` error code.
  - `HttpMediaTypeNotSupportedException` → 415.
  - Arbitrary `RuntimeException` with a message containing a Java class name → 500 body does not contain the class name.
- [ ] 9.6 Write property-based test `IngestControllerPropertyTest` using jqwik:
  - **Property 1** (`@Property`, tag `"Property 1: parsing round-trip fidelity"`): Generate arbitrary valid JSON payload bodies (within size limits) with 1–50 readings. Parse → serialize → re-parse. Assert second parse result equals first (same keys, values, types). **Validates: Requirements 2.8**
  - **Property 7** (`@Property`, tag `"Property 7: error response never exposes internals"`): Generate arbitrary `RuntimeException` instances with messages containing Java class name patterns, `at com.` fragments, and file path separators. Invoke `GlobalExceptionHandler.handleUnexpected()`. Assert response body JSON contains no Java class name pattern (`[A-Z][a-zA-Z]+Exception`), no `at com.` fragment, no file path separator, and conforms to the error response schema. **Validates: Requirements 6.1**
  - **Property 10** (`@Property`, tag `"Property 10: inserted_count + skipped_count invariant"`): Generate valid payloads of N readings (1–50); mock `DuplicateFilter` to return a random partition into K `readingsToInsert` and N-K `skipped`; mock `StorageService` to return K UUIDs. Assert `inserted_count + skipped_count == N`. **Validates: Requirements 10.8**

#### Acceptance Criteria

- All `IngestControllerTest` and `GlobalExceptionHandlerTest` example tests pass.
- Properties 1, 7, and 10 pass with at least 100 iterations each.
- `X-Request-ID` header is present on all non-health-check responses (Requirement 5.6).
- Error responses never contain stack traces, class names, or file paths (Requirement 6.1).
- 201 is returned when `inserted_count > 0`; 200 when `inserted_count == 0` (Requirements 4.5, 5.4).
- `inserted_count + skipped_count` always equals the total number of submitted readings (Requirement 10.8).
- `skipped` array is omitted from the response body when empty (Requirement 5.3).

**Validates**: Requirements 1.1–1.5, 2.1–2.8, 3.1–3.3, 4.5–4.7, 5.1–5.6, 6.1–6.6, 10.6–10.8

---

"""

with open(TASKS_FILE, 'a', encoding='utf-8') as f:
    f.write(TASK9)

print("Task 9 written")
