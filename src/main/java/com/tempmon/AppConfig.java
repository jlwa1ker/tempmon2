package com.tempmon;

import com.tempmon.model.ReadingItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import jakarta.annotation.PostConstruct;
import java.net.URI;

/**
 * Configuration class that reads and validates all environment variables at startup.
 *
 * <p>Required variables: {@code DATABASE_URL}, {@code PORT}.
 * If any required variable is missing or any variable has an out-of-range value,
 * a descriptive error is logged and {@code System.exit(1)} is called before the
 * server begins accepting requests.
 */
@Configuration
public class AppConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Value("${app.database-url:}")
    private String databaseUrl;

    @Value("${server.port:0}")
    private int port;

    @Value("${app.max-payload-bytes:1048576}")
    private long maxPayloadBytes;

    @Value("${app.request-timeout-seconds:30}")
    private int requestTimeoutSeconds;

    @Value("${app.ingest-path:/ingest}")
    private String ingestPath;

    @Value("${app.readings-path:/readings}")
    private String readingsPath;

    /**
     * Validates all configuration values at startup. Logs a descriptive error
     * and exits with code 1 if any required variable is missing or out of range.
     */
    @PostConstruct
    public void validateConfiguration() {
        boolean hasError = false;

        // Validate DATABASE_URL (required, non-empty)
        if (databaseUrl == null || databaseUrl.isBlank()) {
            log.error("Configuration error: required environment variable DATABASE_URL is missing or empty. "
                    + "Please set DATABASE_URL to the DynamoDB endpoint URL.");
            hasError = true;
        }

        // Validate PORT (required, 1-65535)
        if (port < 1 || port > 65535) {
            log.error("Configuration error: environment variable PORT has invalid value '{}'. "
                    + "Expected an integer in the range 1-65535.", port);
            hasError = true;
        }

        // Validate MAX_PAYLOAD_BYTES (minimum 1)
        if (maxPayloadBytes < 1) {
            log.error("Configuration error: environment variable MAX_PAYLOAD_BYTES has invalid value '{}'. "
                    + "Expected a positive integer (minimum 1 byte).", maxPayloadBytes);
            hasError = true;
        }

        // Validate REQUEST_TIMEOUT_SECONDS (1-300)
        if (requestTimeoutSeconds < 1 || requestTimeoutSeconds > 300) {
            log.error("Configuration error: environment variable REQUEST_TIMEOUT_SECONDS has invalid value '{}'. "
                    + "Expected an integer in the range 1-300.", requestTimeoutSeconds);
            hasError = true;
        }

        // Validate INGEST_PATH (non-empty)
        if (ingestPath == null || ingestPath.isBlank()) {
            log.error("Configuration error: environment variable INGEST_PATH is empty. "
                    + "Expected a non-empty URL path string.");
            hasError = true;
        }

        // Validate READINGS_PATH (non-empty)
        if (readingsPath == null || readingsPath.isBlank()) {
            log.error("Configuration error: environment variable READINGS_PATH is empty. "
                    + "Expected a non-empty URL path string.");
            hasError = true;
        }

        if (hasError) {
            System.exit(1);
        }
    }

    /**
     * Configure Spring MVC async request timeout from REQUEST_TIMEOUT_SECONDS.
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(requestTimeoutSeconds * 1000L);
    }

    /**
     * Creates the low-level DynamoDB client configured with the DATABASE_URL endpoint.
     */
    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(databaseUrl))
                .build();
    }

    /**
     * Creates the DynamoDB Enhanced Client wrapping the low-level client.
     */
    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    /**
     * Creates the DynamoDbTable reference for the hygrometer_readings table.
     */
    @Bean
    public DynamoDbTable<ReadingItem> readingsTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table("hygrometer_readings", TableSchema.fromBean(ReadingItem.class));
    }

    // Expose config values for other components that may need them

    public String getDatabaseUrl() {
        return databaseUrl;
    }

    public int getPort() {
        return port;
    }

    public long getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public String getIngestPath() {
        return ingestPath;
    }

    public String getReadingsPath() {
        return readingsPath;
    }
}
