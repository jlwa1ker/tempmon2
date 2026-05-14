
TASKS_FILE = r'c:\Users\walke\git\tempmon2\.kiro\specs\json-http-ingestion\tasks.md'

TASK13 = """
### Task 13: React Frontend — Query Form, Line Chart & Error/Loading States

**Goal**: Build the complete browser UI under `frontend/` that lets users query the `/readings` API and visualize results as a Recharts line chart, with proper loading, error, and empty-state handling.

#### Sub-tasks

- [ ] 13.1 Create `frontend/src/App.jsx` as the root component. Configure Vite to output to `frontend/dist/`.
- [ ] 13.2 Implement the query form component (`QueryForm.jsx`) with:
  - A `start` datetime text input (RFC 3339 format, e.g., `2024-01-15T10:30:00Z`).
  - An `end` datetime text input (RFC 3339 format).
  - A locations input that allows entering one or more location names (e.g., a comma-separated text field or a dynamic tag input). Each value must be 1–255 characters.
  - A metric selector (radio buttons or dropdown) for `temperature_f` (label: "Temperature (°F)") or `humidity_pct` (label: "Humidity (%)").
  - A "Query" submit button.
- [ ] 13.3 Implement client-side validation in `QueryForm.jsx` (Requirement 9.9):
  - `start` or `end` not in RFC 3339 format → display validation message identifying the invalid field; do NOT send the request.
  - No non-empty location values → display validation message; do NOT send the request.
- [ ] 13.4 Implement the fetch logic: on valid form submit, send `GET /readings?start=...&end=...&locations=...&locations=...` using repeated `locations` parameters (Requirement 9.5). Disable the submit button and show a loading indicator while the request is in progress (Requirement 9.10).
- [ ] 13.5 Implement the chart component (`ReadingsChart.jsx`) using Recharts `LineChart`:
  - X-axis: `timestamp` (formatted as a readable date/time string).
  - Y-axis: value of the selected metric, labeled with name and unit (°F or %).
  - One `Line` per distinct `location` in the response, each with a distinct color and a legend label.
  - Render only when the response contains a non-empty `readings` array and there are no active errors (Requirement 9.6).
- [ ] 13.6 Implement empty-state display: when `readings` is an empty array, show "No data found for the selected filters." (Requirement 9.7).
- [ ] 13.7 Implement error-state display: when the API returns an HTTP error, show the HTTP status code and, if the response body is valid JSON with a `message` field, that message; otherwise show the status code and a generic description (Requirement 9.8).
- [ ] 13.8 Re-enable the submit button and remove the loading indicator when the request completes (success or error) (Requirement 9.11).
- [ ] 13.9 Configure Spring Boot to serve the built React app: copy `frontend/dist/` to `src/main/resources/static/` as part of the Maven build (add a Maven plugin step or document the manual copy step). Ensure `GET /` returns `index.html` (Requirement 9.1).
- [ ] 13.10 Write frontend unit tests using Vitest + React Testing Library:
  - Form validation: submitting with invalid `start` format shows validation message and does not call `fetch`.
  - Form validation: submitting with empty locations shows validation message and does not call `fetch`.
  - Loading state: submit button is disabled while fetch is in progress.
  - Empty result: "No data found" message is displayed when `readings` is empty.
  - Error state: error message with status code is displayed on HTTP error response.

#### Acceptance Criteria

- `cd frontend && npm run build` exits 0 and produces `frontend/dist/index.html` (Requirement 9.1).
- The query form sends correctly encoded repeated `locations` parameters (Requirement 9.5).
- Client-side validation prevents requests with invalid RFC 3339 datetimes or empty location lists (Requirement 9.9).
- The Recharts line chart renders one series per location with correct axis labels (Requirement 9.6).
- Loading indicator is shown during fetch; submit button is disabled; both are removed on completion (Requirements 9.10, 9.11).
- Empty and error states display appropriate messages (Requirements 9.7, 9.8).
- All Vitest unit tests pass.

**Validates**: Requirements 9.1–9.11

---

"""

with open(TASKS_FILE, 'a', encoding='utf-8') as f:
    f.write(TASK13)

print("Task 13 written")
