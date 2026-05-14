
TASKS_FILE = r'c:\Users\walke\git\tempmon2\.kiro\specs\json-http-ingestion\tasks.md'

FOOTER = """
---

## Property-Based Test Summary

| Property | Description | Task | Test Class | jqwik Tag |
|---|---|---|---|---|
| Property 1 | Parsing round-trip fidelity | Task 9 | `IngestControllerPropertyTest` | `Property 1: parsing round-trip fidelity` |
| Property 2 | Whitespace-only and empty location rejection | Task 6 | `ReadingValidatorPropertyTest` | `Property 2: whitespace-only and empty location rejection` |
| Property 3 | Out-of-range numeric field rejection | Task 6 | `ReadingValidatorPropertyTest` | `Property 3: out-of-range numeric field rejection` |
| Property 4 | All-or-nothing persistence | Task 14 | `PropertyIntegrationTest` | `Property 4: all-or-nothing persistence` |
| Property 5 | Request ID uniqueness and ordering | Task 8 | `StorageServicePropertyTest` | `Property 5: request ID uniqueness and ordering` |
| Property 6 | Query result ordering and bounds | Task 10 | `QueryHandlerPropertyTest` | `Property 6: query result ordering and bounds` |
| Property 7 | Error response never exposes internals | Task 9 | `IngestControllerPropertyTest` | `Property 7: error response never exposes internals` |
| Property 8 | Validation failure completeness | Task 6 | `ReadingValidatorPropertyTest` | `Property 8: validation failure completeness` |
| Property 9 | Duplicate filter completeness | Task 7 | `DuplicateFilterPropertyTest` | `Property 9: duplicate filter completeness` |
| Property 10 | inserted_count + skipped_count invariant | Task 9 | `IngestControllerPropertyTest` | `Property 10: inserted_count + skipped_count invariant` |

All property-based tests use jqwik and run a minimum of 100 iterations. Properties 4 and 6 require DynamoDB Local; all others use Mockito mocks.

---

## Requirements Coverage Matrix

| Requirement | Tasks |
|---|---|
| Req 1: HTTP Endpoint Availability | 9, 11, 12 |
| Req 2: JSON Parsing | 5, 9 |
| Req 3: Request Validation | 2, 6, 9 |
| Req 4: Data Persistence | 3, 8, 9, 14 |
| Req 5: Response Format | 9, 11, 12 |
| Req 6: Error Handling and Resilience | 9 |
| Req 7: Configuration | 1, 2, 15 |
| Req 8: Data Query API | 10, 11, 14 |
| Req 9: Browser-Based Visualization UI | 1, 13 |
| Req 10: Duplicate Record Handling | 7, 9 |
"""

with open(TASKS_FILE, 'a', encoding='utf-8') as f:
    f.write(FOOTER)

print("Footer written")
