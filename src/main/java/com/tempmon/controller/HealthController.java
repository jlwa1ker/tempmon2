package com.tempmon.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check endpoint.
 * Returns 200 OK with {"status":"ok","version":"major.minor.build"}.
 * The RequestIdFilter excludes the X-Request-ID header from successful health-check responses.
 */
@RestController
public class HealthController {

    @Value("${app.version.major}")
    private String major;

    @Value("${app.version.minor}")
    private String minor;

    @Value("${app.version.build:0}")
    private String build;

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> health() {
        String version = major + "." + minor + "." + build;
        return ResponseEntity.ok(Map.of("status", "ok", "version", version));
    }
}
