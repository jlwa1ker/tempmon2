# Requirements Document

## Introduction

This document defines the requirements for a hygrometer data service. The service accepts JSON payloads via HTTP POST requests containing batches of hygrometer readings (temperature and relative humidity), parses and validates each reading, and persists each one as a separate record in the database. It also exposes a query API for retrieving stored readings by time range and location, and serves a browser-based UI that allows users to visualize temperature and humidity trends over time as line charts. The system is intended to be a reliable, end-to-end solution for ingesting, storing, and exploring hygrometer data.

## Glossary

- **Ingestion_Service**: The HTTP server component that receives and processes incoming POST requests and serves the browser UI.
- **Request_Parser**: The component responsible for reading and parsing the HTTP request body as JSON.
- **Validator**: The component responsible for validating the structure and content of parsed JSON objects.
- **Storage_Layer**: The component responsible for persisting validated Hygrometer_Readings to the database.
- **Query_Handler**: The component of the Ingestion_Service responsible for querying the Database and returning matching Records in response to GET requests.
- **Database**: The persistent storage backend where ingested records are stored.
- **Client**: Any external system or user that sends HTTP POST requests to the Ingestion_Service.
- **Payload**: A valid JSON document with a top-level object structure containing a `readings` array.
- **Hygrometer_Reading**: A single data item within the `readings` array, representing one measurement from a hygrometer at a specific location and time. Each Hygrometer_Reading contains a `timestamp`, `temperature_f`, `humidity_pct`, and `location` field.
- **Record**: A persisted representation of a single Hygrometer_Reading in the Database.
- **Request_ID**: A unique identifier (UUID v4) assigned to each Record at insertion time for traceability, unique within the Database.
- **Hygrometer**: A device that measures temperature and relative humidity.
- **Duplicate_Record**: A Hygrometer_Reading whose `timestamp` and `location` values match those of an existing Record in the Database (i.e., they share the same partition key and sort key). A Duplicate_Record may be an exact match (all field values identical) or a conflicting match (field values differ).

---

## Requirements

### Requirement 1: HTTP Endpoint Availability

**User Story:** As a client, I want to send JSON data to a well-defined HTTP endpoint, so that I can reliably ingest data into the system.

#### Acceptance Criteria

1. THE Ingestion_Service SHALL expose an HTTP POST endpoint at a configurable URL path (default: `/ingest`).
2. WHEN a request is received on any HTTP method other than POST at the ingestion path, THE Ingestion_Service SHALL return an HTTP 405 Method Not Allowed response.
3. WHEN a GET request is received at the designated health check path, THE Ingestion_Service SHALL return an HTTP 200 OK response.
4. WHEN a POST request is received with a `Content-Type` header value of `application/json`, THE Ingestion_Service SHALL accept and process the request body.
5. WHEN a POST request is received with a `Content-Type` header value other than `application/json`, or with no `Content-Type` header, THE Ingestion_Service SHALL return an HTTP 415 Unsupported Media Type response.

---

### Requirement 2: JSON Parsing

**User Story:** As a client, I want the service to correctly parse my JSON payload containing hygrometer readings, so that the data I send is accurately interpreted and stored.

#### Acceptance Criteria

1. WHEN a POST request with a valid JSON body within the configured `MAX_PAYLOAD_BYTES` limit is received, THE Request_Parser SHALL parse the body into an in-memory Payload, preserving all keys, values, and data types exactly as present in the received JSON text.
2. WHEN a POST request body contains malformed JSON, THE Request_Parser SHALL return an HTTP 400 Bad Request response with an error message that identifies the nature of the parse failure (e.g., unexpected token, unterminated string).
3. WHEN a POST request body is empty, THE Request_Parser SHALL return an HTTP 400 Bad Request response with an error message stating that a non-empty JSON body is required.
4. WHEN a POST request body contains valid JSON but the top-level value is not an object (e.g., an array, string, number, boolean, or null), THE Request_Parser SHALL return an HTTP 400 Bad Request response with an error message stating that a top-level JSON object is required.
5. WHEN a POST request body contains a valid top-level JSON object but the object does not contain a `readings` key, THE Request_Parser SHALL return an HTTP 400 Bad Request response with an error message stating that a `readings` array is required.
6. WHEN a POST request body contains a valid top-level JSON object but the value of `readings` is not an array, THE Request_Parser SHALL return an HTTP 400 Bad Request response with an error message stating that `readings` must be a JSON array.
7. WHEN a POST request body contains a valid top-level JSON object with a `readings` key whose value is an empty array, THE Request_Parser SHALL return an HTTP 400 Bad Request response with an error message stating that the `readings` array must contain at least one item.
8. THE Request_Parser SHALL produce a Payload such that parsing, serializing, and re-parsing the body yields a result with the same keys, values, and data types at every level of nesting as the original parsed Payload.

---

### Requirement 3: Request Validation

**User Story:** As a system operator, I want each hygrometer reading in the payload to be validated before storage, so that only well-structured and in-range data enters the database.

#### Acceptance Criteria

1. WHEN all Hygrometer_Readings in the `readings` array pass all validation rules, THE Validator SHALL pass each Hygrometer_Reading to the Storage_Layer for persistence.
2. WHEN one or more Hygrometer_Readings in the `readings` array fail one or more validation rules, THE Validator SHALL return an HTTP 422 Unprocessable Entity response with an error message that lists each individual validation failure, including the zero-based index of the failing item in the array.
3. WHEN the raw request body size in bytes exceeds the configured maximum payload size (`MAX_PAYLOAD_BYTES`), THE Validator SHALL return an HTTP 422 Unprocessable Entity response with an error message identifying the payload size limit. A payload whose size in bytes is exactly equal to `MAX_PAYLOAD_BYTES` SHALL be accepted and processed normally.
4. WHEN a Hygrometer_Reading in the `readings` array is not a JSON object (e.g., it is a number, string, boolean, null, or array), THE Validator SHALL record a validation failure for that item identifying its index and stating that each reading must be a JSON object.
5. WHEN a Hygrometer_Reading does not contain a `timestamp` field, or the `timestamp` field value is not a string conforming to RFC 3339 (ISO 8601 combined date-time with timezone offset, e.g., `2024-01-15T10:30:00Z`), THE Validator SHALL record a validation failure for that item identifying the field name and the expected format.
6. WHEN a Hygrometer_Reading does not contain a `temperature_f` field, or the `temperature_f` field value is not a number, THE Validator SHALL record a validation failure for that item identifying the field name and the expected type.
7. WHEN a Hygrometer_Reading contains a `temperature_f` field whose numeric value is less than -100 or greater than 200, THE Validator SHALL record a validation failure for that item identifying the field name, the received value, and the valid range (-100 to 200 degrees Fahrenheit).
8. WHEN a Hygrometer_Reading does not contain a `humidity_pct` field, or the `humidity_pct` field value is not a number, THE Validator SHALL record a validation failure for that item identifying the field name and the expected type.
9. WHEN a Hygrometer_Reading contains a `humidity_pct` field whose numeric value is less than 0 or greater than 100, THE Validator SHALL record a validation failure for that item identifying the field name, the received value, and the valid range (0 to 100 percent).
10. WHEN a Hygrometer_Reading does not contain a `location` field, or the `location` field value is not a non-empty string of 1 to 255 characters, THE Validator SHALL record a validation failure for that item identifying the field name and the requirement that it be a non-empty string of at most 255 characters.

---

### Requirement 4: Data Persistence

**User Story:** As a system operator, I want each validated hygrometer reading to be stored as its own record in the database, so that the ingested data is durable and individually queryable.

#### Acceptance Criteria

1. WHEN a valid Payload is received, THE Storage_Layer SHALL persist each non-duplicate Hygrometer_Reading in the `readings` array as a separate Record in the Database, storing the `timestamp`, `temperature_f`, `humidity_pct`, and `location` fields with their exact received values.
2. THE Storage_Layer SHALL filter out all Duplicate_Records before any write attempt, and SHALL persist the remaining non-duplicate Records atomically — either all non-duplicate Records are inserted or none are, so that a partial failure does not leave the Database in an inconsistent state.
3. THE Storage_Layer SHALL assign a unique Request_ID (UUID v4) to each Record at the time of insertion, unique within the Database.
4. THE Storage_Layer SHALL record the UTC timestamp of ingestion for each Record with millisecond precision in ISO 8601 format.
5. WHEN the write attempt for non-duplicate Records succeeds, THE Ingestion_Service SHALL return an HTTP 201 Created response if at least one Record was inserted, or an HTTP 200 OK response if `inserted_count` equals zero (including when all Hygrometer_Readings were duplicates or the write operation results in zero insertions for any reason).
6. IF the Database is unavailable at the time of a write attempt and the connection completely fails, THEN THE Ingestion_Service SHALL return an HTTP 503 Service Unavailable response and log an error message that includes the database host/port and the underlying connection error.
7. IF a write attempt fails due to a database constraint violation, THEN THE Ingestion_Service SHALL return an HTTP 500 Internal Server Error response and log the specific database error.

---

### Requirement 5: Response Format

**User Story:** As a client, I want consistent, machine-readable responses from the service, so that I can programmatically handle success and error cases.

#### Acceptance Criteria

1. THE Ingestion_Service SHALL return all responses with a `Content-Type: application/json` header.
2. WHEN a request succeeds, THE Ingestion_Service SHALL return a JSON response body containing `"status": "success"`, `inserted_count` (a non-negative integer equal to the number of Records actually inserted), `skipped_count` (a non-negative integer equal to the number of Duplicate_Records skipped), and `request_ids` (an array of UUID v4 strings, one per inserted Record, in the same order as the corresponding non-duplicate items in the `readings` array).
3. WHEN a request succeeds and one or more Hygrometer_Readings were skipped as Duplicate_Records, THE Ingestion_Service SHALL include a `skipped` array in the response body where each element contains `index` (the zero-based index of the skipped reading in the original `readings` array) and `reason` (either `"exact_match"` for an identical Duplicate_Record or `"conflicting_data"` for a conflicting Duplicate_Record). WHEN no Hygrometer_Readings were skipped, THE Ingestion_Service SHALL omit the `skipped` field or return it as an empty array.
4. THE Ingestion_Service SHALL return an HTTP 201 Created response when at least one Record was inserted (`inserted_count` is greater than zero), and an HTTP 200 OK response when all Hygrometer_Readings were skipped (`inserted_count` equals zero).
5. WHEN a request fails, THE Ingestion_Service SHALL return a JSON response body containing `"status": "error"`, `error` (a non-empty uppercase snake_case machine-readable error code string, e.g., `INVALID_PAYLOAD`), and `message` (a human-readable description of the failure).
6. WHEN responding to any request that is not a successful health-check endpoint request, THE Ingestion_Service SHALL include a unique `X-Request-ID` header in the response containing a UUID v4 generated per-request, distinct from the per-Record `Request_ID` values assigned by the Storage_Layer. Failed health-check responses SHALL also include the `X-Request-ID` header.

---

### Requirement 6: Error Handling and Resilience

**User Story:** As a system operator, I want the service to handle unexpected errors gracefully, so that transient failures do not crash the service or expose internal details to clients.

#### Acceptance Criteria

1. IF an unhandled internal error occurs, THEN THE Ingestion_Service SHALL return an HTTP 500 Internal Server Error response with an error message that does not contain stack traces, internal class names, file paths, or internal identifiers. An HTTP 500 response SHALL be returned for unhandled errors regardless of whether a timeout condition is also present.
2. IF an unhandled internal error occurs, THEN THE Ingestion_Service SHALL log the full error details, including stack trace and Request_ID, to the application log.
3. WHEN a request results in any error (handled or unhandled), THE Ingestion_Service SHALL continue to accept and respond to subsequent requests.
4. THE Ingestion_Service SHALL enforce a configurable request timeout between 1 and 300 seconds (default: 30 seconds).
5. WHEN a request exceeds the configured timeout and no other error has occurred, THE Ingestion_Service SHALL return an HTTP 408 Request Timeout response.
6. WHEN a request both exceeds the configured timeout and triggers an unhandled internal error, THE Ingestion_Service SHALL return an HTTP 500 Internal Server Error response, treating the internal error as higher priority regardless of which condition was detected first.

---

### Requirement 7: Configuration

**User Story:** As a system operator, I want the service to be configurable without code changes, so that I can adapt it to different environments and requirements.

#### Acceptance Criteria

1. WHEN the Ingestion_Service starts, THE Ingestion_Service SHALL read all configuration values from environment variables before accepting any requests.
2. THE Ingestion_Service SHALL support configuration of the database connection string via the environment variable `DATABASE_URL`.
3. THE Ingestion_Service SHALL support configuration of the HTTP listening port (valid range: 1–65535) via the environment variable `PORT`.
4. THE Ingestion_Service SHALL support configuration of the maximum payload size in bytes (minimum: 1 byte) via the environment variable `MAX_PAYLOAD_BYTES`.
5. THE Ingestion_Service SHALL support configuration of the request timeout in seconds (valid range: 1–300) via the environment variable `REQUEST_TIMEOUT_SECONDS`.
6. IF a required environment variable (`DATABASE_URL`, `PORT`) is absent at startup, THEN THE Ingestion_Service SHALL log an error message that names the missing variable and exit with a non-zero exit code. The service SHALL only exit with a non-zero exit code when configuration is actually invalid; a fully valid configuration SHALL result in a normal startup.
7. IF an environment variable is present but its value is outside the valid range or cannot be parsed to the expected type, THEN THE Ingestion_Service SHALL log an error message identifying the variable name, the invalid value, and the expected format, and exit with a non-zero exit code. IF the error logging itself fails, THE Ingestion_Service SHALL block startup and not proceed until the error has been successfully logged.

---

### Requirement 8: Data Query API

**User Story:** As a browser client, I want to query stored hygrometer readings by time range and location, so that I can retrieve the data needed to render a visualization.

#### Acceptance Criteria

1. THE Ingestion_Service SHALL expose an HTTP GET endpoint at a configurable URL path (default: `/readings`).
2. WHEN a GET request is received at the `/readings` path with valid `start`, `end`, and `locations` query parameters, THE Ingestion_Service SHALL return an HTTP 200 OK response with a JSON body containing a `readings` array of matching Records.
3. WHEN a GET request is received at the `/readings` path and the `start` or `end` query parameter is absent, THE Ingestion_Service SHALL return an HTTP 400 Bad Request response with an error message identifying the missing parameter by name.
4. WHEN a GET request is received at the `/readings` path and the `start` or `end` query parameter value is not a string conforming to RFC 3339 (ISO 8601 combined date-time with timezone offset, e.g., `2024-01-15T10:30:00Z`), THE Ingestion_Service SHALL return an HTTP 400 Bad Request response with an error message identifying the invalid parameter and the expected format.
5. WHEN a GET request is received at the `/readings` path and the `end` datetime is not strictly after the `start` datetime, THE Ingestion_Service SHALL return an HTTP 400 Bad Request response with an error message stating that `end` must be after `start`.
6. WHEN a GET request is received at the `/readings` path, the `locations` query parameter SHALL be provided as one or more repeated query parameters (e.g., `locations=Kitchen&locations=Garage`), each value being a non-empty string of 1 to 255 characters. IF the `locations` parameter is absent or all provided values are empty strings, THE Ingestion_Service SHALL return an HTTP 400 Bad Request response with an error message stating that at least one location name is required.
7. WHEN a GET request is received at the `/readings` path with valid parameters, THE Query_Handler (a component of the Ingestion_Service) SHALL query the Database for all Records whose `timestamp` is greater than or equal to `start` and less than or equal to `end`, and whose `location` exactly matches one of the provided location names.
8. WHEN the query returns results, THE Ingestion_Service SHALL return a JSON response body containing a `readings` array, where each element contains the `timestamp`, `temperature_f`, `humidity_pct`, and `location` fields of the matching Record, ordered by `timestamp` ascending, and capped at a maximum of 10,000 records. IF the result set exceeds 10,000 records, THE Ingestion_Service SHALL include a `truncated: true` field in the response body.
9. WHEN the query returns no results, THE Ingestion_Service SHALL return an HTTP 200 OK response with a JSON response body containing an empty `readings` array.
10. IF the Database is unavailable at the time of a query attempt and the connection completely fails, THEN THE Ingestion_Service SHALL return an HTTP 503 Service Unavailable response and log an error message that includes the database host/port and the underlying connection error.
11. WHEN a request is received on any HTTP method other than GET at the `/readings` path, THE Ingestion_Service SHALL return an HTTP 405 Method Not Allowed response.

---

### Requirement 9: Browser-Based Visualization UI

**User Story:** As a user, I want a browser-based interface for querying and visualizing hygrometer data, so that I can explore temperature and humidity trends across locations over time without writing code.

#### Acceptance Criteria

1. THE Ingestion_Service SHALL serve a static HTML page at the root path (`/`) that contains the complete browser UI as a self-contained HTML document with embedded or co-served CSS and JavaScript.
2. THE UI SHALL provide a start datetime input and an end datetime input, each accepting values in RFC 3339 format (e.g., `2024-01-15T10:30:00Z`).
3. THE UI SHALL provide an input control for the user to enter or select one or more location names (each 1 to 255 characters) to include in the query.
4. THE UI SHALL provide a control for the user to choose whether to display temperature in degrees Fahrenheit (`temperature_f`) or relative humidity in percent (`humidity_pct`).
5. WHEN the user submits a query, THE UI SHALL send a GET request to the `/readings` endpoint with the entered `start` datetime, `end` datetime, and `locations` values as query parameters using repeated parameter encoding (e.g., `locations=Kitchen&locations=Garage`).
6. WHEN the `/readings` endpoint returns a non-empty `readings` array and the UI is in a clean state with no active errors or validation messages, THE UI SHALL render a line chart where the x-axis represents time, the y-axis represents the value of the selected metric labeled with its name and unit (°F for temperature, % for humidity), and each distinct location in the response is rendered as a separate labeled series.
7. WHEN the `/readings` endpoint returns an empty `readings` array, THE UI SHALL display a message to the user stating that no data was found for the selected filters.
8. WHEN the `/readings` endpoint returns an HTTP error response, THE UI SHALL display a human-readable error message to the user that includes the HTTP status code and, if the response body is valid JSON containing a `message` field, that message; otherwise the UI SHALL display the HTTP status code and a generic error description.
9. WHEN the user submits a query and the start or end datetime is not in RFC 3339 format, or the location list contains no non-empty entries, THE UI SHALL display a validation message identifying the invalid field and SHALL NOT send a request to the server.
10. WHILE a query request is in progress, THE UI SHALL display a loading indicator and disable the submit control to prevent duplicate submissions. WHEN the UI first loads before any request has been made, THE UI SHALL keep the submit control enabled.
11. WHEN a query completes (successfully or with an error), THE UI SHALL re-enable the submit control and remove the loading indicator.

---

### Requirement 10: Duplicate Record Handling

**User Story:** As a system operator, I want the service to detect and handle duplicate readings gracefully, so that re-transmitted or overlapping payloads do not corrupt the database or cause unnecessary errors.

#### Acceptance Criteria

1. THE Ingestion_Service SHALL define a Duplicate_Record as any Hygrometer_Reading whose `timestamp` and `location` values match those of an existing Record in the Database.
2. WHEN two or more Hygrometer_Readings within the same Payload share the same `timestamp` and `location` values (intra-batch duplicate), THE Ingestion_Service SHALL log a WARNING for each occurrence after the first, regardless of whether the remaining field values are identical or different. The first occurrence SHALL be treated as the candidate for insertion; all subsequent intra-batch duplicates SHALL be skipped.
3. WHEN a Hygrometer_Reading matches an existing Database Record on `timestamp` and `location` AND the `temperature_f` and `humidity_pct` values are identical to those of the existing Record (exact-match inter-batch duplicate), THE Ingestion_Service SHALL silently skip that reading without producing a log entry.
4. WHEN a Hygrometer_Reading matches an existing Database Record on `timestamp` and `location` BUT the `temperature_f` or `humidity_pct` values differ from those of the existing Record (conflicting inter-batch duplicate), THE Ingestion_Service SHALL skip that reading and log a WARNING to the application log.
5. THE Ingestion_Service SHALL filter out all Duplicate_Records before any write attempt, so that only non-duplicate Hygrometer_Readings are submitted to the Storage_Layer.
6. WHEN the set of non-duplicate Hygrometer_Readings is non-empty, THE Storage_Layer SHALL persist those Records atomically (all-or-nothing). IF the write attempt fails due to a database error, THE Ingestion_Service SHALL reject the entire remaining batch and the Client must retransmit.
7. WHEN the set of non-duplicate Hygrometer_Readings is empty (all readings in the Payload were duplicates), THE Ingestion_Service SHALL return an HTTP 200 OK response with `inserted_count` equal to zero and `skipped_count` equal to the total number of Hygrometer_Readings in the Payload. WHEN the Payload contains zero Hygrometer_Readings, THE Ingestion_Service SHALL return an HTTP 200 OK response with `inserted_count` equal to zero and `skipped_count` equal to zero.
8. THE Ingestion_Service SHALL include `inserted_count` and `skipped_count` in every success response such that `inserted_count` plus `skipped_count` equals the total number of Hygrometer_Readings submitted in the Payload.
