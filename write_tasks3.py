
TASKS_FILE = r'c:\Users\walke\git\tempmon2\.kiro\specs\json-http-ingestion\tasks.md'

TASK5_6 = """
### Task 5: PayloadParser — Implementation & Unit Tests

**Goal**: Implement `PayloadParser` to convert a raw HTTP body string into a `Payload` record, rejecting all structurally invalid inputs with typed errors that map to HTTP 400.

#### Sub-tasks

- [ ] 5.1 Create `PayloadParser` as a Spring `@Service`. Inject Jackson `ObjectMapper`.
- [ ] 5.2 Implement `parse(String rawBody)` returning `Payload`:
  - Empty or null body → throw `ParseException` with code `EMPTY_BODY`.
  - `JsonProcessingException` from Jackson → throw `ParseException` with code `MALFORMED_JSON` and the Jackson error detail.
  - Top-level node is not an object → throw `ParseException` with code `INVALID_PAYLOAD_STRUCTURE`.
  - No `readings` key → throw `ParseException` with code `INVALID_PAYLOAD_STRUCTURE`.
  - `readings` value is not an array → throw `ParseException` with code `INVALID_PAYLOAD_STRUCTURE`.
  - `readings` array is empty → throw `ParseException` with code `EMPTY_READINGS`.
  - Valid input → return `Payload` with the list of `JsonNode` elements from the `readings` array.
- [ ] 5.3 Write `PayloadParserTest` (JUnit 5) covering all branches listed in 5.2 plus a valid payload with 1 reading and a valid payload with 50 readings.

#### Acceptance Criteria

- All `PayloadParserTest` cases pass.
- `ParseException` carries the correct error code string for each failure mode (Requirement 2.2–2.7).
- A valid payload with N readings produces a `Payload` whose `readings` list has exactly N elements (Requirement 2.1).
- The parser does not perform field-level validation — that is `ReadingValidator`'s responsibility.

**Validates**: Requirements 2.1–2.7

---

### Task 6: ReadingValidator — Implementation, Unit Tests & Property-Based Tests

**Goal**: Implement `ReadingValidator` to validate each `JsonNode` reading against all field rules, collecting all failures before returning, and verify correctness with both example-based and property-based tests.

#### Sub-tasks

- [ ] 6.1 Create `ReadingValidator` as a Spring `@Service`.
- [ ] 6.2 Implement `validate(Payload payload)` returning `ValidationResult`:
  - For each element in `payload.readings()`, at its zero-based index:
    - If the node is not a JSON object → record `ValidationFailure(index, "item", "each reading must be a JSON object")`.
    - If `timestamp` is absent or not a string parseable by `OffsetDateTime.parse()` → record failure for `timestamp`.
    - If `temperature_f` is absent or not numeric → record failure for `temperature_f` (type error).
    - If `temperature_f` is numeric but outside [-999, 999] → record failure for `temperature_f` (range error, include value and range).
    - If `humidity_pct` is absent or not numeric → record failure for `humidity_pct` (type error).
    - If `humidity_pct` is numeric but outside [0, 100] → record failure for `humidity_pct` (range error, include value and range).
    - If `location` is absent, not a string, empty, whitespace-only, or longer than 255 characters → record failure for `location`.
  - Collect ALL failures across ALL readings before returning.
  - If any failures exist → return `ValidationResult.invalid(failures)`.
  - If no failures → return `ValidationResult.valid(readings)` where each `HygrometerReading` is constructed from the validated node values.
- [ ] 6.3 Write `ReadingValidatorTest` (JUnit 5) covering: all-valid batch; missing each required field; `temperature_f` at boundaries (-999, 999, -999.1, 999.1); `humidity_pct` at boundaries (0, 100, -0.1, 100.1); `location` at length boundaries (1 char, 255 chars, 256 chars, empty string, whitespace-only string); non-object reading item; multiple failures in one payload (all present in result).
- [ ] 6.4 Write property-based test `ReadingValidatorPropertyTest` using jqwik:
  - **Property 2** (`@Property`, tag `"Property 2: whitespace-only and empty location rejection"`): Generate `HygrometerReading`-shaped JSON nodes where `location` is drawn from strings of only whitespace characters (space `\\u0020`, tab `\\u0009`, newline `\\u000A`) of length 0–255. Assert `validate` returns `Invalid` with exactly one failure for `location`. **Validates: Requirements 3.10**
  - **Property 3** (`@Property`, tag `"Property 3: out-of-range numeric field rejection"`): Generate readings where `temperature_f` is outside [-999, 999] OR `humidity_pct` is outside [0, 100] (or both). Assert `validate` returns `Invalid`; the failure list contains an entry for each out-of-range field naming the field, the received value, and the valid range. **Validates: Requirements 3.7, 3.9**
  - **Property 8** (`@Property`, tag `"Property 8: validation failure completeness"`): Generate payloads where a random non-empty subset of readings have one or more invalid fields. Assert the failure list contains exactly one entry per failing reading; each entry's `index` matches the reading's position; no failing reading is omitted. **Validates: Requirements 3.2**

#### Acceptance Criteria

- All `ReadingValidatorTest` example tests pass.
- Properties 2, 3, and 8 pass with at least 100 iterations each.
- Validation is non-fail-fast: a payload with 3 invalid readings produces 3 (or more) failure entries (Requirement 3.2).
- Whitespace-only `location` values of any length 0–255 are rejected (Requirement 3.10).
- Range failures include the received value and the valid range in the message (Requirements 3.7, 3.9).

**Validates**: Requirements 3.1–3.10

---

"""

with open(TASKS_FILE, 'a', encoding='utf-8') as f:
    f.write(TASK5_6)

print("Tasks 5-6 written")
