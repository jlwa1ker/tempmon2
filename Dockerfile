# Stage 1: Build React frontend
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm install
COPY frontend/ ./
RUN npm run build

# Stage 2: Build Spring Boot backend
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:go-offline
COPY src/ ./src/
COPY --from=frontend-build /app/frontend/dist/ ./src/main/resources/static/
RUN mvn package -DskipTests

# Stage 3: Minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend-build /app/target/*.jar app.jar

ENV SERVER_PORT=8080
ENV DYNAMODB_TABLE_NAME=temperature_readings
ENV AWS_REGION=us-east-1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
