
TASKS_FILE = r'c:\Users\walke\git\tempmon2\.kiro\specs\json-http-ingestion\tasks.md'

TASK14_15 = """
### Task 14: Integration Tests (Spring Boot Test + DynamoDB Local)

**Goal**: Verify the full request-response cycle end-to-end against a real DynamoDB Local instance, covering the happy path and all major error paths.

#### Sub-tasks

- [ ] 14.1 Add DynamoDB Local (or LocalStack) as a test dependency and configure it to start before the Spring Boot test context loads (e.g., via a JUnit 5 extension or `@DynamicPropertySource`).
- [ ] 14.2 Create the `hygrometer_readings` table in DynamoDB Local before each test class using the AWS SDK (`CreateTableRequest` with `location` as partition key and `timestamp` as sort key).
- [ ] 14.3 Write `IngestIntegrationTest` (`@SpringBootTest`, `@AutoConfigureMockMvc`) covering:
  - Valid payload (2 readings) → 201; `inserted_count=2`; records queryable via `GET /readings`.
  - Valid payload with 1 duplicate (exact match) → 200 or 201 depending on count; `skipped_count=1`, `reason="exact_match"`.
  - Invalid payload (missing `temperature_f`) → 422; no records written to DynamoDB.
  - Payload exceeding `MAX_PAYLOAD_BYTES` → 422 with `PAYLOAD_TOO_LARGE`.
  - Wrong `Content-Type` → 415.
  - Wrong HTTP method (`GET /ingest`) → 405.
  - Empty body → 400 with `EMPTY_BODY`.
  - Malformed JSON → 400 with `MALFORMED_JSON`.
- [ ] 14.4 Write `QueryIntegrationTest` (`@SpringBootTest`, `@AutoConfigureMockMvc`) covering:
  - Pre-seed 5 records across 2 locations; `GET /readings` with valid params → 200 with correct records sorted by timestamp.
  - `GET /readings` with `start` after all records → 200 with empty `readings` array.
  - Missing `start` param → 400.
  - `end` before `start` → 400.
  - Missing `locations` → 400.
  - Database unavailable (stop DynamoDB Local mid-test) → 503.
- [ ] 14.5 Write `PropertyIntegrationTest` using jqwik + DynamoDB Local:
  - **Property 4** (`@Property`, tag `"Property 4: all-or-nothing persistence"`): Generate valid reading lists of 1–50 items. Success path: call `StorageService.persist(readings)`; query DynamoDB; assert exactly N records present with matching field values. Failure path: inject a fault on the second batch (mock `transactWriteItems` to throw on second call); call `persist`; assert zero records from that payload remain. **Validates: Requirements 4.2**
- [ ] 14.6 Ensure all integration tests clean up (delete all records from the table) after each test method to prevent cross-test contamination.

#### Acceptance Criteria

- All integration tests pass against DynamoDB Local.
- Property 4 passes with at least 100 iterations.
- The full POST `/ingest` → GET `/readings` round-trip works end-to-end (Requirements 4.1, 8.2).
- Invalid payloads produce no DynamoDB writes (Requirement 3.1).
- `MAX_PAYLOAD_BYTES` enforcement works at the filter level (Requirement 3.3).
- Database unavailability returns 503 (Requirements 4.6, 8.10).

**Validates**: Requirements 1.1–1.5, 2.1–2.7, 3.1–3.3, 4.1–4.7, 5.1–5.6, 8.1–8.11

---

### Task 15: Dockerfile & GitHub Actions CI/CD Pipeline

**Goal**: Containerize the application and automate the full build-test-deploy pipeline so that every push to `main` results in a new Docker image deployed to ECS Fargate.

#### Sub-tasks

- [ ] 15.1 Write a `Dockerfile` at the project root using a multi-stage build:
  - **Stage 1 (frontend build)**: Use `node:lts-alpine`; copy `frontend/`; run `npm ci && npm run build`.
  - **Stage 2 (Java build)**: Use `maven:3-eclipse-temurin-<LTS>-alpine`; copy `pom.xml` and `src/`; copy the `dist/` output from Stage 1 into `src/main/resources/static/`; run `mvn verify -B` (compiles, tests, packages).
  - **Stage 3 (runtime)**: Use `eclipse-temurin:<LTS>-jre-alpine`; copy the fat JAR from Stage 2; set `ENTRYPOINT ["java", "-jar", "app.jar"]`; expose `${PORT}`.
- [ ] 15.2 Create `.github/workflows/ci-cd.yml` with:
  - **Triggers**: `push` to `main`; `pull_request` targeting `main`.
  - **Runner**: `ubuntu-latest`.
  - **Steps**:
    1. `actions/checkout@v4`
    2. Set up Node.js (LTS) with `actions/setup-node@v4`.
    3. `cd frontend && npm ci && npm run build` — build the React app.
    4. Copy `frontend/dist/` to `src/main/resources/static/`.
    5. Set up Java (latest LTS) with `actions/setup-java@v4` (Temurin distribution).
    6. `mvn verify -B` — compile, run all tests (unit + integration), package.
    7. Configure AWS credentials with `aws-actions/configure-aws-credentials@v4` (using OIDC or repository secrets `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`).
    8. Log in to Amazon ECR with `aws-actions/amazon-ecr-login@v2`.
    9. Build and push Docker image to ECR: tag as `<ECR_REGISTRY>/<ECR_REPOSITORY>:<github.sha>` and `:latest`.
    10. Retrieve the current ECS task definition with `aws ecs describe-task-definition`.
    11. Update the container image in the task definition with `aws-actions/amazon-ecs-render-task-definition@v1`.
    12. Deploy to ECS Fargate with `aws-actions/amazon-ecs-deploy-task-definition@v2` (wait for service stability).
  - Steps 7–12 run only on `push` to `main` (not on pull requests).
- [ ] 15.3 Document required GitHub Actions secrets in `README.md` or a `docs/deployment.md` file: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`, `ECR_REGISTRY`, `ECR_REPOSITORY`, `ECS_CLUSTER`, `ECS_SERVICE`, `ECS_TASK_DEFINITION`, `CONTAINER_NAME`.
- [ ] 15.4 Document required ECS task definition environment variables: `DATABASE_URL`, `PORT`, `MAX_PAYLOAD_BYTES`, `REQUEST_TIMEOUT_SECONDS`, and optionally `INGEST_PATH`, `READINGS_PATH`. Note that the ECS task role must have IAM permissions for `dynamodb:PutItem`, `dynamodb:GetItem`, `dynamodb:BatchGetItem`, `dynamodb:TransactWriteItems`, `dynamodb:Query`, `dynamodb:DeleteItem`.
- [ ] 15.5 Verify the Dockerfile builds locally (`docker build -t hygrometer-service .`) with `DATABASE_URL` and `PORT` set as build args or noted as runtime-only variables.

#### Acceptance Criteria

- `docker build -t hygrometer-service .` exits 0 (Requirement 7, deployment).
- The GitHub Actions workflow runs `mvn verify` (all tests) before building the Docker image.
- The workflow deploys only on push to `main`, not on pull requests (tech-stack steering file).
- The ECS task definition environment variable documentation covers all required variables (Requirement 7.1–7.5).
- The IAM permissions list covers all DynamoDB operations used by the service.
- The multi-stage Dockerfile produces a minimal runtime image (JRE only, no JDK or Maven in the final stage).

**Validates**: Requirements 7.1–7.7 (deployment configuration), tech-stack CI/CD requirements

---

"""

with open(TASKS_FILE, 'a', encoding='utf-8') as f:
    f.write(TASK14_15)

print("Tasks 14-15 written")
