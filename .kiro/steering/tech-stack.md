# Tech Stack & Project Standards

## Backend

- **Language**: Java (use the latest LTS version)
- **Framework**: Spring Boot
- **Build tool**: Maven (standard Spring Boot project layout)
- **HTTP layer**: Spring Web MVC (REST controllers)
- **Validation**: Jakarta Bean Validation (Hibernate Validator)
- **Testing**: JUnit 5 + Mockito + jqwik (property-based testing); use Spring Boot Test for integration tests

## Database

- **Database**: Amazon DynamoDB
- **Access**: AWS SDK for Java v2 (`software.amazon.awssdk:dynamodb`)
- **Local development**: Use DynamoDB Local or LocalStack for local testing
- Do NOT use JPA/Hibernate or any relational ORM — DynamoDB is a NoSQL key-value/document store

## Frontend

- **Framework**: React (functional components with hooks)
- **Charting**: Recharts
- **Build tool**: Vite
- **Language**: JavaScript (no TypeScript required unless explicitly requested)
- The React app is built separately and its output (`dist/`) is served as static files by the Spring Boot server

## Project Structure

Follow standard Spring Boot project layout:

```
src/
  main/
    java/         # Application source code
    resources/
      static/     # Built React frontend (dist/ output copied here)
      application.properties
  test/
    java/         # Unit and integration tests
pom.xml
frontend/         # React app source (Vite project)
infra/            # CloudFormation template
```

## CI/CD

- **CI**: GitHub Actions (`Build & Test` workflow in `.github/workflows/ci.yml`)
- **Deploy**: Separate manual workflow (`Deploy` in `.github/workflows/deploy.yml`) triggered via `workflow_dispatch` with a build number
- **Deployment target**: AWS ECS on Fargate
- **Infrastructure**: CloudFormation template in `infra/cloudformation.yml`
- Build workflow should:
  1. Build the React frontend (`npm run build` in `frontend/`)
  2. Copy the frontend `dist/` output to `src/main/resources/static/`
  3. Run `mvn verify` to compile, test, and package the Spring Boot app
  4. Build a Docker image and push it to Amazon ECR (on push to `main` only)
- Deploy workflow:
  1. Accepts a build number as input
  2. Updates the ECS task definition to use the image tagged with that build number
  3. Forces a new ECS deployment and waits for service stability
- Trigger the build workflow on push to `main` and on pull requests targeting `main`
- Use the latest Ubuntu runner (`ubuntu-latest`)
- The Spring Boot app must be containerized with a `Dockerfile` at the project root
- The ECS task role must have IAM permissions to access DynamoDB
- GitHub Actions authenticates to AWS via OIDC (no static access keys)
- Configuration (e.g. `DATABASE_URL`, `PORT`) is passed to the container via ECS task definition environment variables

## General Conventions

- Configuration via environment variables (mapped through `application.properties` using `${ENV_VAR}` syntax)
- All API responses use `Content-Type: application/json`
- Timestamps are ISO 8601 / RFC 3339 strings with timezone offset
- UUIDs are UUID v4, generated with `java.util.UUID.randomUUID()`
- Log using SLF4J with Logback (Spring Boot default)
- Do not expose stack traces or internal class names in HTTP error responses
