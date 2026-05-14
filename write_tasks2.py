
TASKS_FILE = r'c:\Users\walke\git\tempmon2\.kiro\specs\json-http-ingestion\tasks.md'

TASK1 = """
### Task 1: Project Scaffolding

**Goal**: Create the Maven/Spring Boot project skeleton and the React/Vite frontend scaffold so that all subsequent tasks have a compilable base to build on.

#### Sub-tasks

- [ ] 1.1 Generate a Spring Boot Maven project (`HygrometerApplication`) targeting the latest Java LTS, with dependencies: `spring-boot-starter-web`, `spring-boot-starter-validation`, `software.amazon.awssdk:dynamodb`, `software.amazon.awssdk:dynamodb-enhanced`, `jackson-databind`, `slf4j-api` / Logback (via Spring Boot default), `jqwik` (test scope), `junit-jupiter` (test scope), `mockito-core` (test scope).
- [ ] 1.2 Create the standard Maven directory layout: `src/main/java`, `src/main/resources`, `src/test/java`, `src/test/resources`.
- [ ] 1.3 Add a placeholder `application.properties` that maps environment variables to Spring properties (`server.port=${PORT}`, `app.database-url=${DATABASE_URL}`, `app.max-payload-bytes=${MAX_PAYLOAD_BYTES:1048576}`, `app.request-timeout-seconds=${REQUEST_TIMEOUT_SECONDS:30}`, `app.ingest-path=${INGEST_PATH:/ingest}`, `app.readings-path=${READINGS_PATH:/readings}`).
- [ ] 1.4 Scaffold the React/Vite frontend under `frontend/` using `npm create vite@latest frontend -- --template react`. Add `recharts` as a dependency.
- [ ] 1.5 Verify the Spring Boot app compiles and starts (`mvn spring-boot:run`) with `DATABASE_URL` and `PORT` set to dummy values (startup will fail on DynamoDB connection, but the JVM must start).
- [ ] 1.6 Verify the React app builds (`cd frontend && npm install && npm run build`) and produces a `dist/` directory.

#### Acceptance Criteria

- `mvn compile` exits 0 with no errors.
- `cd frontend && npm run build` exits 0 and produces `frontend/dist/index.html`.
- The project layout matches the structure defined in the tech-stack steering file.

**Validates**: Requirements 7 (configuration via env vars), 9.1 (static file serving foundation)

---

### Task 2: Configuration & Startup Validation (AppConfig)

**Goal**: Implement `AppConfig` so the service reads, validates, and exposes all environment-variable-backed configuration at startup, and exits with a non-zero code if any required variable is missing or invalid.

#### Sub-tasks

- [ ] 2.1 Create `AppConfig` as a `@Configuration` class that reads `DATABASE_URL`, `PORT`, `MAX_PAYLOAD_BYTES`, `REQUEST_TIMEOUT_SECONDS`, `INGEST_PATH`, and `READINGS_PATH` from the environment (via `@Value` or `@ConfigurationProperties`).
- [ ] 2.2 Validate each variable: `DATABASE_URL` non-empty; `PORT` in [1, 65535]; `MAX_PAYLOAD_BYTES` >= 1; `REQUEST_TIMEOUT_SECONDS` in [1, 300]. Log a descriptive error naming the variable, the invalid value, and the expected format, then call `System.exit(1)` on any failure.
- [ ] 2.3 Produce `DynamoDbClient`, `DynamoDbEnhancedClient`, and `DynamoDbTable<ReadingItem>` beans from `DATABASE_URL` (used as the DynamoDB endpoint override for local/test; in production the SDK uses the standard endpoint resolution).
- [ ] 2.4 Set `spring.mvc.async.request-timeout` programmatically from `REQUEST_TIMEOUT_SECONDS` (in milliseconds).
- [ ] 2.5 Register a `OncePerRequestFilter` bean that enforces `MAX_PAYLOAD_BYTES`: if the `Content-Length` header or actual body size exceeds the limit, return 422 with `PAYLOAD_TOO_LARGE` before the body reaches the controller.
- [ ] 2.6 Write unit tests (`AppConfigTest`) covering: missing `DATABASE_URL` exits 1; missing `PORT` exits 1; `PORT=0` exits 1; `PORT=65535` starts normally; `REQUEST_TIMEOUT_SECONDS=0` exits 1; `REQUEST_TIMEOUT_SECONDS=300` starts normally; `MAX_PAYLOAD_BYTES=0` exits 1.

#### Acceptance Criteria

- Starting the app without `DATABASE_URL` set logs an error naming `DATABASE_URL` and exits with code 1 (Requirement 7.6).
- Starting the app with `PORT=99999` logs an error naming `PORT`, the value `99999`, and the valid range, then exits with code 1 (Requirement 7.7).
- Starting the app with all valid variables produces the three DynamoDB beans without error.
- `MAX_PAYLOAD_BYTES` filter returns 422 with `PAYLOAD_TOO_LARGE` for oversized bodies (Requirement 3.3).
- All `AppConfigTest` unit tests pass.

**Validates**: Requirements 7.1–7.7, 3.3

---

### Task 3: DynamoDB Table Setup & ReadingItem Bean

**Goal**: Define the `ReadingItem` DynamoDB-annotated bean and the table name constant so all persistence components share a single, correct mapping.

#### Sub-tasks

- [ ] 3.1 Create `ReadingItem` as a `@DynamoDbBean` class with fields: `location` (`@DynamoDbPartitionKey`), `timestamp` (`@DynamoDbSortKey`, stored as UTC ISO 8601 string), `requestId` (String), `temperatureF` (BigDecimal), `humidityPct` (BigDecimal), `ingestedAt` (String, UTC ISO 8601 with ms precision). Include no-arg constructor, getters, and setters.
- [ ] 3.2 Add `@DynamoDbAttribute` annotations with the exact DynamoDB attribute names (`request_id`, `temperature_f`, `humidity_pct`, `ingested_at`) to match the table schema in the design document.
- [ ] 3.3 Define a constant `TABLE_NAME = "hygrometer_readings"` accessible to `AppConfig` when creating the `DynamoDbTable<ReadingItem>` bean.
- [ ] 3.4 Write a unit test (`ReadingItemTest`) that constructs a `ReadingItem`, sets all fields, and asserts that getters return the correct values — confirming the bean is correctly wired.

#### Acceptance Criteria

- `ReadingItem` compiles with all required `@DynamoDbBean` annotations.
- The partition key attribute is `location` and the sort key attribute is `timestamp` (Requirement 4.1, design DynamoDB table schema).
- `ReadingItemTest` passes.

**Validates**: Requirements 4.1, 4.3, 4.4

---

### Task 4: Core Domain Models

**Goal**: Define all in-memory Java records and result types used across the pipeline so that Tasks 5–10 can reference them without circular dependencies.

#### Sub-tasks

- [ ] 4.1 Create `HygrometerReading` record: `OffsetDateTime timestamp`, `BigDecimal temperatureF`, `BigDecimal humidityPct`, `String location`.
- [ ] 4.2 Create `SkippedReading` record: `int index`, `String reason`. Reason values are the string constants `"exact_match"` and `"conflicting_data"`.
- [ ] 4.3 Create `FilterResult` record: `List<HygrometerReading> readingsToInsert`, `List<SkippedReading> skipped`.
- [ ] 4.4 Create `InsertResult` record: `List<String> requestIds`.
- [ ] 4.5 Create `ValidationFailure` record: `int index`, `String field`, `String message`.
- [ ] 4.6 Create `ValidationResult` sealed interface (or class) with two states: `Valid` (containing `List<HygrometerReading>`) and `Invalid` (containing `List<ValidationFailure>`). Provide static factory methods `valid(List<HygrometerReading>)` and `invalid(List<ValidationFailure>)`.
- [ ] 4.7 Create `Payload` record: `List<com.fasterxml.jackson.databind.JsonNode> readings` (raw JSON nodes, to be converted by `ReadingValidator`).
- [ ] 4.8 Create typed exceptions: `ParseException` (extends `RuntimeException`), `DatabaseUnavailableException` (extends `RuntimeException`), `DatabaseConstraintException` (extends `RuntimeException`). Each carries a `String message` and optional `Throwable cause`.
- [ ] 4.9 Create `QueryResult` record: `List<HygrometerReading> readings`, `boolean truncated`.
- [ ] 4.10 Write a unit test (`DomainModelsTest`) that constructs each record/type and asserts field accessors return the expected values.

#### Acceptance Criteria

- All domain model classes compile with no errors.
- `DomainModelsTest` passes.
- No domain model class imports from `StorageService`, `DuplicateFilter`, or any controller — they are pure data types.

**Validates**: Requirements 4.1–4.4, 5.2, 5.3, 10.8 (structural foundation)

---

"""

with open(TASKS_FILE, 'a', encoding='utf-8') as f:
    f.write(TASK1)

print("Tasks 1-4 written")
