package com.tempmon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Hygrometer data ingestion and query service.
 *
 * <p>All environment-variable validation is performed by {@link AppConfig}
 * during application-context startup. If any required variable is missing or
 * out of range, {@code AppConfig} logs a descriptive error and calls
 * {@code System.exit(1)} before the server begins accepting requests.
 */
@SpringBootApplication
public class HygrometerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HygrometerApplication.class, args);
    }
}
