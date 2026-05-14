# Tech Stack & Project Standards

## Backend

- **Language**: Java (use the latest LTS version)
- **Framework**: Spring Boot
- **Build tool**: Maven (standard Spring Boot project layout)
- **HTTP layer**: Spring Web MVC (REST controllers)
- **Validation**: Jakarta Bean Validation (Hibernate Validator)
- **Testing**: JUnit 5 + Mockito; use Spring Boot Test for integration tests

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
```

## CI/CD

- **CI**: GitHub Actions
- **Deployment target**: AWS ECS on Fargate
- Build workflow should:
  1. Build the React frontend (`npm run build` in `frontend/`)
  2. Copy the frontend `dist/` output to `src/main/resources/static/`
  3. Run `mvn verify` to compile, test, and package the Spring Boot app
  4. Build a Docker image and push it to Amazon ECR
  5. Deploy the new image to an ECS Fargate service by updating the ECS task definition
- Trigger the workflow on push to `main` and on pull requests targeting `main` (deploy only on push to `main`)
- Use the latest Ubuntu runner (`ubuntu-latest`)
- The Spring Boot app must be containerized with a `Dockerfile` at the project root
- The ECS task role must have IAM permissions to access DynamoDB
- Configuration (e.g. `DATABASE_URL`, `PORT`) is passed to the container via ECS task definition environment variables or AWS Secrets Manager

## General Conventions

- Configuration via environment variables (mapped through `application.properties` using `${ENV_VAR}` syntax)
- All API responses use `Content-Type: application/json`
- Timestamps are ISO 8601 / RFC 3339 strings with timezone offset
- UUIDs are UUID v4, generated with `java.util.UUID.randomUUID()`
- Log using SLF4J with Logback (Spring Boot default)
- Do not expose stack traces or internal class names in HTTP error responses
